# MoveInSync Settlement Engine — Evaluation Q&A Bank (50+ Questions)

### Category 1: Project Overview & Business Domain

#### Q1: What core business problem does the MoveInSync Settlement Engine solve?
**Model Answer:** It automates corporate transport billing and driver compensation for enterprise commute fleets. Manual billing suffers from calculation drift, double-billing, delayed vendor payments, and disputes. Our engine processes tens of thousands of trips, evaluates mid-month rate changes, performance bonuses, safety penalties, and Indian Section 194C TDS tax with 0.00 paise drift, offering three-way invoice reconciliation and dispute resolution.

#### Q2: Who are the target users and personas?
**Model Answer:**
1. `SETTLEMENT_ADMIN`: Triggers batch calculation runs and previews billing ledgers.
2. `APPROVER`: Finance managers who review and approve settlement runs and dispute tickets.
3. `VENDOR_ADMIN`: Fleet owners who submit GST tax invoices and review company payslips.
4. `DRIVER`: Commute drivers who view itemized payslips and raise trip dispute tickets.

#### Q3: Why is corporate transport billing more complex than standard ride-hailing?
**Model Answer:** Unlike on-demand apps where each ride is billed immediately to a rider's card, corporate transport involves:
- Monthly consolidated billing cycles across corporate accounts.
- Dual duty models (`PER_TRIP` vs `PER_DUTY`).
- Rate card contracts that change mid-month (effective date intervals).
- Frequency-capped SLA penalties (e.g. over-speeding capped at 2 deductions per month).
- Indian statutory Section 194C TDS tax with threshold exemptions.
- Vendor invoice reconciliation and dispute deltas.

---

### Category 2: Architecture & System Design

#### Q4: Why did you choose a Modular Monolith instead of Microservices?
**Model Answer:** For a core financial billing engine, transactional consistency (ACID) and atomic multi-table updates are critical. A distributed microservice architecture introduces distributed transaction complexity (Sagas, 2PC), network latency, and eventual consistency risks. Our modular monolith provides clean package separation, SRP calculators, and outbox isolation without distributed failure modes.

#### Q5: What is the Transactional Outbox Pattern and why is it used?
**Model Answer:** It avoids the Dual-Write problem (updating the database and sending a message to a message broker/bank API in two uncoordinated steps). Instead of calling the bank API during the HTTP request, the event is saved to an `outbox_event` table in the *same* database transaction as the settlement state transition. An asynchronous worker claims and delivers pending events. If the server crashes, the event remains in the database and is retried.

#### Q6: How does PostgreSQL `FOR UPDATE SKIP LOCKED` work in the outbox processor?
**Model Answer:** Standard `SELECT FOR UPDATE` blocks multiple concurrent worker instances when they compete for rows. `SKIP LOCKED` tells PostgreSQL to skip any rows currently locked by other transactions and return only unlocked rows. This allows multiple outbox workers across multiple servers to process events in parallel with zero lock contention and zero duplicate disbursements.

---

### Category 3: Financial Precision & Math

#### Q7: Why is `double` or `float` strictly forbidden for financial calculations?
**Model Answer:** `double` and `float` use binary IEEE-754 representation, which cannot represent base-10 decimals like `0.1` or `0.01` exactly. Over 10,000 trips, floating-point rounding errors accumulate into significant discrepancies (paise drift). We use Java's `BigDecimal` with an explicit scale of 2 and `RoundingMode.HALF_UP` on every arithmetic step, ensuring exact mathematical parity.

#### Q8: How does the engine prove zero mathematical drift?
**Model Answer:** In `ThousandsOfTripsStressTest`, we evaluate 10,000 trips across 50 drivers. We assert two mathematical invariants:
1. Every driver payslip's net pay strictly equals the sum of its credit and debit line items.
2. The settlement run's aggregate total net pay strictly equals the sum of all individual driver payslips.
The test verifies `run.totalNet.compareTo(sumOfPayslips) == 0` (0.00 paise drift).

#### Q9: How is statutory Indian Section 194C TDS withholding calculated?
**Model Answer:** Under Section 194C of the Indian Income Tax Act:
- Cumulative gross earnings up to the monthly exemption threshold (e.g. ₹30,000) are exempt.
- Earnings exceeding the threshold are taxed at 1% for individual/HUF drivers and 2% for company contracts.
- Formula: `TaxableExcess = max(0, TaxableEarnings - MonthlyThreshold)`
- `TDS = (TaxableExcess * RatePercent) / 100`, signed as a negative debit.

---

### Category 4: Concurrency, Idempotency & Database

#### Q10: How does the two-tier idempotency system work?
**Model Answer:**
- **Tier 1 (HTTP Request Level):** Handled via `@Idempotent` AOP. Clients provide an `Idempotency-Key` header. The request payload is hashed with SHA-256. If a repeated request arrives with the same key and hash, the cached JSON response is returned immediately with zero recalculation.
- **Tier 2 (Domain Scope Level):** A PostgreSQL partial unique index on `(vendor_id, business_unit_id, period_start, period_end)` enforces that only one non-failed original settlement run can ever exist for a billing cycle.

#### Q11: How do you prevent race conditions when two managers approve a payout simultaneously?
**Model Answer:** We use database-level pessimistic locking (`PessimisticLocking`). `SettlementRunRepository.findByIdWithLock()` executes `SELECT ... FOR UPDATE`, obtaining an exclusive row lock in PostgreSQL. The first manager transitions the status from `UNDER_REVIEW` to `APPROVED`. The second manager's transaction is blocked until the first completes; when unblocked, the state machine detects the run is already `APPROVED` and rejects the second call with an `InvalidStateTransitionException` (HTTP 422).

#### Q12: How do you handle JVM crashes during settlement processing?
**Model Answer:** The `StaleRunRecoveryTask` runs periodically as a background scheduled job. It queries `settlement_run` for records stuck in `PROCESSING` status past a 30-minute threshold. It marks them as `FAILED`, logs an `AuditEvent`, and unblocks subsequent billing runs under the partial unique index.

---

### Category 5: Invoice Reconciliation & Dispute Deltas

#### Q13: How does three-way invoice reconciliation work?
**Model Answer:** The vendor submits a tax invoice claiming a billed amount. The `ReconciliationService` fetches the finalized `SettlementRun` ledger. The `ReconciliationCalculator` evaluates the absolute difference `|Calculated - Billed|`. If variance is within the tolerance threshold (default ₹50.00 or configurable percentage), the invoice transitions to `MATCHED`. If variance exceeds tolerance, it marks `DISCREPANCY` for manual auditing.

#### Q14: Why do disputes use "Append-Only Deltas" instead of recalculating the original settlement run?
**Model Answer:** Financial and tax ledgers require strict audit immutability. If a past run were recalculated and updated in place:
1. Past tax filings (TDS returns) and accounting records would be corrupted.
2. Historical invoices already paid would no longer match the database.
Instead, we compute only the delta `Delta = Revised - Original`, store it in an append-only `SettlementAdjustment` table, and issue a dedicated adjustment `PayoutInstruction`. Historical records remain untouched.

---

### Category 6: Examiner "Tricky" Questions

#### Q15: How would you scale this engine to 1 million trips per month?
**Model Answer:**
1. **Partitioning:** Partition `trip` and `settlement_line` tables by billing month (`period_start`).
2. **Batch Parallelism:** Partition the 1M trips by `vendor_id` and process vendors concurrently using Spring Batch or Java 21 Virtual Threads.
3. **Database Read Replicas:** Point preview calculations and read-only queries to PostgreSQL read replicas.
4. **Caching:** Keep contract rates and policies in Redis/Caffeine cache to eliminate repetitive DB lookups.

#### Q16: What happens if an outbox banking event fails permanently?
**Model Answer:** Each event has a `retry_count`. If a payment gateway failure occurs, the retry count is incremented. When it reaches `MAX_RETRIES = 3`, the event transitions to `FAILED` (Dead Letter Queue status), the corresponding `PayoutInstruction` is marked `FAILED` with error details, and an error log is emitted for operations alerting.
