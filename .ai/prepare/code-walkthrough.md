# MoveInSync Settlement Engine — Critical Code Walkthrough

This guide details the 5 most critical code snippets in the codebase with line-by-line technical explanations for evaluation walkthroughs.

---

### Snippet 1: Section 194C Cumulative TDS Formula
**File:** `src/main/java/com/moveinsync/settlement/calculation/TaxCalculator.java`

```java
public Optional<CalculatedLineItem> calculate(Driver driver, BigDecimal taxableEarnings, TaxConfig config) {
    if (driver == null || taxableEarnings == null || config == null) {
        return Optional.empty();
    }

    BigDecimal threshold = config.getMonthlyThreshold() != null
            ? config.getMonthlyThreshold()
            : BigDecimal.ZERO;

    // Evaluate earnings strictly exceeding statutory threshold
    BigDecimal taxableExcess = taxableEarnings.subtract(threshold);
    if (taxableExcess.compareTo(BigDecimal.ZERO) <= 0) {
        return Optional.empty();
    }

    BigDecimal rate = config.getEffectiveRate(driver);
    BigDecimal tdsAmount = taxableExcess.multiply(rate)
            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

    return Optional.of(CalculatedLineItem.builder()
            .driver(driver)
            .componentType(SettlementLine.ComponentType.TDS_194C)
            .amount(tdsAmount.negate()) // Signed negative deduction
            .description(String.format("TDS u/s 194C @ %s%% on excess ₹%s", rate, taxableExcess))
            .build());
}
```
**Explanation:**
1. `taxableExcess.compareTo(BigDecimal.ZERO) <= 0`: Guards against deducting tax if the driver's monthly earnings did not exceed the ₹30,000 threshold.
2. `config.getEffectiveRate(driver)`: Resolves 1% for individual drivers (with PAN) or 2% for corporate fleets.
3. `.divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)`: Performs exact division with banker's half-up rounding.
4. `tdsAmount.negate()`: Explicitly signs the tax as a debit deduction so net pay arithmetic is purely additive: `Net = Gross + sum(LineItems)`.

---

### Snippet 2: Zero-Drift Financial Invariant Assertion
**File:** `src/main/java/com/moveinsync/settlement/payout/validator/FinancialInvariantValidator.java`

```java
public void validateZeroDrift(SettlementRun run, List<DriverPayslip> payslips) {
    BigDecimal sumOfPayslips = payslips.stream()
            .map(DriverPayslip::getNetPay)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    if (run.getTotalNet().compareTo(sumOfPayslips) != 0) {
        throw new PolicyViolationException(String.format(
                "Financial integrity check failed: Run total net (%s) does not match sum of payslips (%s)",
                run.getTotalNet().toPlainString(), sumOfPayslips.toPlainString()
        ));
    }
}
```
**Explanation:**
- Asserts that the mathematical sum of individual driver payslips equals the settlement run aggregate net pay.
- Executed before payout finalization. If even 1 paise of drift exists, the transaction rolls back immediately, preventing corrupt bank disbursements.

---

### Snippet 3: Non-Blocking Outbox Claim via PostgreSQL `SKIP LOCKED`
**File:** `src/main/java/com/moveinsync/settlement/repository/OutboxEventRepository.java`

```java
@Query(value = """
    SELECT * FROM outbox_event
    WHERE status = 'PENDING'
    ORDER BY created_at ASC
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
""", nativeQuery = true)
List<OutboxEvent> findPendingEventsForProcessing(@Param("limit") int limit);
```
**Explanation:**
- `FOR UPDATE`: Acquires row-level exclusive locks on pending rows.
- `SKIP LOCKED`: Instructs PostgreSQL to skip rows already locked by other worker instances.
- Enables horizontally scalable background workers with zero distributed locking overhead.

---

### Snippet 4: Immutable Closed-Loop Dispute Delta Calculation
**File:** `src/main/java/com/moveinsync/settlement/calculation/DisputeDeltaCalculator.java`

```java
public DisputeDeltaResult calculateDelta(BigDecimal originalAmount, BigDecimal revisedAmount) {
    if (revisedAmount == null || revisedAmount.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Revised amount must be specified and non-negative");
    }

    BigDecimal original = (originalAmount != null ? originalAmount : BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal revised = revisedAmount.setScale(2, RoundingMode.HALF_UP);
    BigDecimal delta = revised.subtract(original).setScale(2, RoundingMode.HALF_UP);

    boolean payable = delta.compareTo(BigDecimal.ZERO) > 0;
    boolean negative = delta.compareTo(BigDecimal.ZERO) < 0;

    return DisputeDeltaResult.builder()
            .originalAmount(original)
            .revisedAmount(revised)
            .deltaAmount(delta)
            .payable(payable)
            .negativeAdjustment(negative)
            .build();
}
```
**Explanation:**
- `delta = revised.subtract(original)`: Computes strictly the differential amount.
- Original settlement lines are never modified; an append-only `SettlementAdjustment` is recorded.
