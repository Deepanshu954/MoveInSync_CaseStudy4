# Engineering Decisions & Trade-offs
## MoveInSync — Vendor & Driver Settlement Engine

This document explains the significant engineering decisions made during the design and implementation of the Settlement Engine, including the alternatives considered and the reasons each decision was made.

---

## 1. Pure-Function Financial Calculators

**Decision:** Extract all financial math into stateless `@Component` classes (`BasePayCalculator`, `IncentiveCalculator`, `PenaltyCalculator`, `TaxCalculator`) with zero database or Spring dependencies.

**Why:**
- Makes the core business logic unit-testable without any infrastructure — no Docker, no DB, no Spring context needed
- Enables **preview mode**: the same calculators run without persisting anything when `previewOnly: true`
- The 10,000-trip stress test validates mathematical correctness in pure JVM memory in < 200 ms
- If calculation logic needs to change (e.g., a tax rate update), changes are isolated to a single file

**Alternative considered:** Embedding calculation logic inside `SettlementService`
**Why rejected:** Would require a full Spring application context (or heavy mocking) for unit tests. Cannot run preview without special-casing throughout the service.

---

## 2. `BigDecimal` with `RoundingMode.HALF_UP` Everywhere

**Decision:** Use `BigDecimal` for all currency values. `double` and `float` are never used in any financial calculation.

**Why:** Floating-point arithmetic cannot exactly represent most decimal fractions. For example:
```
0.1 + 0.2 = 0.30000000000000004  (double)
0.1 + 0.2 = 0.3                  (BigDecimal)
```
In a settlement with 10,000 trips, these small errors accumulate into visible rupee differences that cannot pass audit.

`HALF_UP` rounding is the standard for Indian financial and tax calculations (and matches how banks round NEFT amounts).

**Financial invariant enforced at finalization:**
```
|run.totalNet − Σ(payslip.netPay)| ≤ ₹0.01
```
The `FinancialInvariantValidator` throws `PolicyViolationException` if this invariant is violated — no money moves unless the math checks out exactly.

---

## 3. Half-Open Rate Card Intervals `[effectiveFrom, effectiveTo)`

**Decision:** Contract rates use half-open time intervals. The `effectiveTo` timestamp is exclusive.

**Why:** Vendors frequently renegotiate rates mid-month. A driver completing a trip on October 15 at 08:00 AM should get the *new* rate if it becomes effective from midnight on October 15, not the old one.

```java
.filter(r -> !completedAt.isBefore(r.getEffectiveFrom()))    // completedAt >= from
.filter(r -> r.getEffectiveTo() == null ||
             completedAt.isBefore(r.getEffectiveTo()))        // completedAt < to
.max(Comparator.comparing(ContractRate::getEffectiveFrom)
               .thenComparing(ContractRate::getVersion))
```

The version tie-breaking ensures that if two rates have the same `effectiveFrom` (possible after a correction), the highest version wins.

**Alternative considered:** Date-only intervals (inclusive on both ends)
**Why rejected:** Ambiguous for trips that complete exactly at midnight on the changeover date. Timestamp intervals are unambiguous.

---

## 4. Two-Tier Idempotency

**Decision:** Implement idempotency at two independent layers that together guarantee no duplicate settlement runs.

**Tier 1 — HTTP (IdempotencyService):**
- SHA-256 hash of the request body is stored alongside the `Idempotency-Key` header
- Returns the cached response for COMPLETED keys
- Returns HTTP 409 if the same key is submitted with a different payload (tampering detection)
- Returns HTTP 409 if the same key is currently PROCESSING (duplicate in-flight)

**Tier 2 — Database (Partial Unique Index):**
```sql
CREATE UNIQUE INDEX idx_settlement_run_original_scope
    ON settlement_run(vendor_id, business_unit_id, period_start, period_end)
    WHERE run_type = 'ORIGINAL' AND status != 'FAILED';
```

**Why both are needed:**
- Tier 1 alone has a TOCTOU (time-of-check to time-of-use) race: two requests could both pass the check before either writes the response
- Tier 2 alone catches the race at the DB level, but returns a raw SQL error rather than a meaningful API response
- Together, Tier 1 gives good UX (structured JSON response, cached result) and Tier 2 provides an absolute safety net

---

## 5. Transactional Outbox (Over Kafka / Async HTTP)

**Decision:** Use the Transactional Outbox pattern with PostgreSQL `SKIP LOCKED` instead of calling the banking API directly or using a message broker.

**Problem:** Settlement finalization must persist payouts AND notify the banking gateway. Doing both in a single operation creates a distributed transaction — if the DB write succeeds but the API call fails, money is owed but not disbursed. If the API call is made before the DB write and the DB fails, payment is triggered but the system has no record of it.

**Outbox solution:**
1. In the *same database transaction* as finalization: save `PayoutInstruction` + `OutboxEvent`
2. A separate background thread (`OutboxProcessorTask`) reads and dispatches events using `SKIP LOCKED`
3. Each event dispatch is its own transaction — partial failures are isolated

**Why SKIP LOCKED over Kafka:**
- No additional infrastructure to deploy, monitor, or secure
- PostgreSQL is already in the stack — outbox is a zero-cost addition
- `SKIP LOCKED` safely supports multiple application instances competing to process events
- For the scale of this case study (hundreds of payouts per month, not millions per second), PostgreSQL outbox is more than sufficient

**Failure handling:**
- Failed events increment `retry_count` and remain in the queue
- After exceeding max retries, events move to `status = FAILED` (DLQ)
- Manual retry endpoints: `POST /api/payouts/outbox/retry/{eventId}` and `/retry-all`

---

## 6. Append-Only Dispute Resolution

**Decision:** When a trip billing dispute is approved, the original `SettlementLine` records are never modified. Instead:
1. A `SettlementAdjustment` record is appended with the `deltaAmount`
2. A new `PayoutInstruction` is created for the delta only
3. A new `OutboxEvent` queues the delta disbursement

**Why:**
- Financial records must be **immutable after finalization** — this is a regulatory requirement in most jurisdictions
- An auditor must be able to reconstruct the exact calculation that produced the original payout at any future point in time
- The delta model naturally handles both increases (underpayment disputes) and decreases (overpayment corrections)

**Alternative considered:** Cancelling the original payout and creating a new one for the full revised amount
**Why rejected:** More complex reversal accounting, higher risk of double-payment, and loses the original audit record

---

## 7. Pessimistic Locking on State Transitions

**Decision:** Use `SELECT FOR UPDATE` (pessimistic locking) when fetching a `SettlementRun` for state transitions (`approve`, `moveToReview`, `finalizeRun`).

**Why:** Settlement approval is rare, high-stakes, and must not result in two concurrent approvers both successfully transitioning the same run. Pessimistic locking ensures correctness at the cost of slightly lower concurrency (acceptable since approvals happen once per billing cycle per vendor).

**Alternative considered:** Optimistic locking with a `@Version` column
**Why rejected:** Optimistic locking raises an `OptimisticLockingFailureException` after the transaction completes, requiring the client to retry. For a one-time financial approval, it is more user-friendly to queue the request (via the `FOR UPDATE` lock) than to ask the approver to resubmit.

---

## 8. Spring Cache for Policy Data

**Decision:** Use `@Cacheable` via `PolicyCacheService` to cache contract rates, incentive policies, and penalty policies for the duration of a settlement batch.

**Why:** For a single billing run of N trips across M drivers:
- Without cache: N×M DB queries to fetch the same policy data
- With cache: 3-5 DB queries (one per policy type), then in-memory lookups

A `ConcurrentMapCacheManager` (Spring's default simple cache) is sufficient since the cache is only needed for the duration of a single HTTP request and doesn't need to survive restarts.

---

## 9. MDC-Based Distributed Tracing

**Decision:** Inject a `X-Trace-Id` header into every request thread via `TraceIdFilter` and include it in all log output via MDC (Mapped Diagnostic Context).

**Why:** When debugging a production issue, a support engineer needs to correlate all log lines from a single settlement calculation — across service calls, DB queries, and background tasks. MDC tracing provides this without requiring a dedicated tracing system (Jaeger, Zipkin).

The `traceId` is also persisted in `AuditEvent.correlationId`, enabling cross-entity audit trail correlation long after the request is complete.

---

## 10. Java 21 + Spring Boot 4.1.1

**Decision:** Use Java 21 (LTS) and Spring Boot 4.1.1 (latest at time of writing).

**Why Java 21:**
- Virtual threads (Project Loom) available for future async I/O improvements
- Pattern matching, records, and sealed classes improve domain modeling
- Guaranteed long-term support

**Why Spring Boot 4.1.1:**
- Latest stable release, requiring Java 21 minimum
- Native support for Spring AOT, GraalVM native images (future optimization path)
- All dependencies in the Spring ecosystem updated to latest compatible versions
