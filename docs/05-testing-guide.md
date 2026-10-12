# Testing Guide
## MoveInSync — Vendor & Driver Settlement Engine

---

## Overview

The test suite is structured in three tiers:

| Tier | Type | Infrastructure | Count |
|---|---|---|---|
| **Tier 1** | Pure unit tests — financial calculators | None | 21 tests |
| **Tier 2** | Integration tests — full lifecycle | Testcontainers (real PostgreSQL) | 6+ test classes |
| **Tier 3** | Stress test — 10,000 trips, math invariants | None | 1 test (4 assertions) |

---

## Running the Tests

### All Tests
```bash
./mvnw clean test
```
> **Requires Docker** — integration tests use Testcontainers to spin up a real PostgreSQL 16 instance automatically.

### Unit Tests Only (No Docker Needed)
```bash
./mvnw test -Dtest="BasePayCalculatorTest,IncentiveCalculatorTest,PenaltyCalculatorTest,TaxCalculatorTest,ReconciliationCalculatorTest,DisputeDeltaCalculatorTest,PayoutCalculatorTest,ThousandsOfTripsStressTest"
```

### Single Test Class
```bash
./mvnw test -Dtest="ThousandsOfTripsStressTest"
```

---

## Tier 1: Calculator Unit Tests

All calculator unit tests are pure JUnit 5 + AssertJ. They require zero infrastructure (no Spring, no DB, no Docker).

### `BasePayCalculatorTest`

Tests the rate card resolution logic.

| Test | Scenario |
|---|---|
| `testPerTripCalculationWithCurrentRate` | 5 trips all after rate start — all get the current rate |
| `testMidMonthRateRevision` | 3 trips before Oct 15 @ ₹250, 2 trips after @ ₹275 |
| `testNoRateForEarlyTrip` | Trip before any rate's `effective_from` → returns empty list (logs ERROR) |

**Key assertion:**
```java
// Mid-month rate revision: trips before Oct 15 get ₹250, trips on/after get ₹275
assertThat(lines.stream()
    .filter(l -> l.amount().compareTo(new BigDecimal("250.00")) == 0)
    .count()).isEqualTo(3);

assertThat(lines.stream()
    .filter(l -> l.amount().compareTo(new BigDecimal("275.00")) == 0)
    .count()).isEqualTo(2);
```

---

### `IncentiveCalculatorTest`

| Test | Scenario |
|---|---|
| `testOnTimeBonusEarned` | 95 on-time trips out of 100 (95%) ≥ 90% threshold → bonus awarded |
| `testOnTimeBonusNotEarned` | 85 on-time trips (85%) < 90% threshold → no bonus |
| `testWorkedDaysBonusEarned` | 22 working days ≥ 20 threshold → bonus awarded |

---

### `PenaltyCalculatorTest`

| Test | Scenario |
|---|---|
| `testPenaltyWithDailyCap` | 3 OVER_SPEED violations on the same day, cap is 2 → only 2 charged |

**Key assertion:**
```java
// 3 violations, cap 2 per day → only 2 × ₹500 = ₹1000 charged
BigDecimal expected = new BigDecimal("1000.00");
BigDecimal actual = lines.stream()
    .map(CalculatedLineItem::amount)
    .map(BigDecimal::abs)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
assertThat(actual).isEqualByComparingTo(expected);
```

---

### `TaxCalculatorTest`

| Test | Scenario |
|---|---|
| `testTdsAboveThreshold` | Gross ₹50,000 − threshold ₹30,000 = ₹20,000 taxable @ 10% → TDS = ₹2,000 |
| `testTdsBelowThreshold` | Gross ₹25,000 < threshold → TDS = ₹0 |
| `testTdsRateAsPercent` | Same as above but rate stored as `10.00` (not `0.10`) |

---

### `ReconciliationCalculatorTest`

| Test | Scenario |
|---|---|
| `testExactMatch` | Invoice amount == calculated amount → MATCHED |
| `testWithinAbsoluteTolerance` | Delta ₹30 < tolerance ₹50 → MATCHED |
| `testExceedsAbsoluteTolerance` | Delta ₹75 > tolerance ₹50 → DISCREPANCY |
| `testExceedsPercentTolerance` | Delta > 0.5% of calculated → DISCREPANCY |

---

### `DisputeDeltaCalculatorTest`

| Test | Scenario |
|---|---|
| `testPositiveDelta` | Revised ₹350 > original ₹250 → delta = +₹100 (underpayment correction) |
| `testNegativeDelta` | Revised ₹200 < original ₹250 → delta = −₹50 (overpayment correction) |
| `testZeroDelta` | Revised == original → delta = ₹0 (no payout instruction generated) |
| `testDeltaWithNull` | Null revised amount → IllegalArgumentException |

---

### `PayoutCalculatorTest`

| Test | Scenario |
|---|---|
| `testFinancialInvariantHolds` | Run total matches sum of payslips → invariant passes |
| `testFinancialInvariantViolation` | Deliberate ₹0.02 mismatch → PolicyViolationException thrown |

---

## Tier 2: Integration Tests (Testcontainers)

Integration tests use `@SpringBootTest` with a real PostgreSQL 16 instance managed by Testcontainers. Each test class starts with a clean, migrated database.

### `SettlementLifecycleIntegrationTest`

Tests the full settlement workflow end-to-end through the REST API:

1. `POST /api/v1/auth/login` → get JWT
2. `POST /api/settlements/calculate` → CALCULATED status
3. `POST /api/settlements/{id}/review` → UNDER_REVIEW
4. `POST /api/settlements/{id}/approve` → APPROVED
5. `POST /api/settlements/{id}/finalize` → FINALIZED
6. Assert: `PayoutInstruction` records created for all drivers
7. Assert: `OutboxEvent` records created in PENDING status

---

### `IdempotencyIntegrationTest`

1. Submit a settlement calculation with `Idempotency-Key: KEY-001`
2. Submit the *exact same request* again with the same key → returns HTTP 200 with the *same* run ID (cached)
3. Submit with the same key but *different payload* → returns HTTP 409

---

### `OutboxDispatchIntegrationTest`

1. Finalize a settlement run (creates OutboxEvents)
2. `POST /api/payouts/outbox/process`
3. Assert: all outbox events transition from `PENDING` to `SENT`
4. Assert: `PayoutInstruction` status transitions to `SUCCESS` (mock bank responds OK)

---

### `ReconciliationIntegrationTest`

1. Finalize a settlement run
2. Submit vendor invoice for the same period
3. Reconcile invoice vs. run
4. Assert MATCHED for amounts within tolerance
5. Assert DISCREPANCY for amounts outside tolerance

---

### `DisputeIntegrationTest`

1. Finalize a settlement run
2. Raise a dispute on a specific trip
3. Resolve the dispute (approved with a revised amount)
4. Assert: a `SettlementAdjustment` record is created
5. Assert: a new `PayoutInstruction` for the *delta only* is created
6. Assert: original settlement lines are **unchanged**

---

### `StaleRunRecoveryIntegrationTest`

1. Insert a settlement run in `PROCESSING` status with `updated_at` 31 minutes ago
2. Trigger `StaleRunRecoveryTask` manually
3. Assert: run transitions to `FAILED`
4. Assert: audit event `STALE_RUN_RECOVERED_TO_FAILED` is persisted

---

## Tier 3: 10,000-Trip Stress Test

`ThousandsOfTripsStressTest` — the mathematical proof of zero monetary drift.

### Setup
```
Drivers:   50
Trips:     200 per driver = 10,000 total
Period:    30 calendar days (Oct 1–30, 2026)
Rates:     ₹250/trip before Oct 15, ₹275/trip on/after (mid-month revision)
Violations: 10 per driver = 500 total (OVER_SPEED, 2 per day cap)
Incentives: ON_TIME_BONUS = ₹500 if ≥ 90% on-time, WORKED_DAYS_BONUS = ₹250 if ≥ 20 days
TDS:       10% on gross exceeding ₹30,000/month
```

### Assertions

**1. Per-driver line item check (paisa-exact):**
```java
// For every driver, net pay = sum of signed line items
BigDecimal lineItemSum = driverLines.stream()
    .map(CalculatedLineItem::amount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
assertThat(payslip.netPay()).isEqualByComparingTo(lineItemSum);
```

**2. Run-level aggregate check:**
```java
// Run total net = sum of all driver payslips
BigDecimal payslipSum = payslips.stream()
    .map(DriverPayslip::netPay)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
assertThat(runResult.totalNet()).isEqualByComparingTo(payslipSum);
```

**3. Financial equation check:**
```java
// Net == Gross + Incentives - Penalties - TDS (for every driver)
BigDecimal expected = payslip.grossPay()
    .add(payslip.incentives())
    .subtract(payslip.penalties())
    .subtract(payslip.tdsDeducted());
assertThat(payslip.netPay()).isEqualByComparingTo(expected);
```

**4. Performance assertion:**
```java
// 10,000 trips must complete in under 2 seconds
assertThat(durationMs).isLessThan(2000L);
```

### Sample Output
```
Thousands of Trips Stress Test Passed in 161 ms across 10,000 trips!
  - Total Line Items: 10119
  - Total Gross:      ₹3,502,177.65
  - Total Incentives: ₹56,250.00
  - Total Penalties:  ₹8,400.00
  - Total TDS:        ₹20,584.27
  - Net Disbursed:    ₹3,529,443.38
  - Exact Math Drift: 0.00 paise ✓
```

---

## Test Configuration

Integration tests use `application-test.yml` (or environment variable overrides) to connect to the Testcontainers PostgreSQL instance. Flyway runs all migrations automatically before each test class.

No manual database setup is needed — `./mvnw clean test` is the only command required.
