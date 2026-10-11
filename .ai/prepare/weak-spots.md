# MoveInSync Settlement Engine — Defense & Weak Spots Guide

This document highlights potential examiner traps and how to defend every architectural decision.

---

### 1. Potential Examiner Trap: *"Why didn't you use Double or Float with formatting?"*
- **The Trap:** An examiner might argue that formatting a `double` with `String.format("%.2f", val)` is simpler and faster.
- **Your Defense:** 
  > *"Floating-point numbers in Java use IEEE-754 binary floating-point representation, which cannot accurately represent binary fractions like 0.1 or 0.01. Over thousands of calculations, compounding rounding errors accumulate into noticeable paise drift. In enterprise banking, even a 1-paise variance fails audit reconciliation. We use `BigDecimal` with explicit scale and `RoundingMode.HALF_UP` to guarantee exact mathematical determinism."*

---

### 2. Potential Examiner Trap: *"Why not just call the Payment Gateway directly in the Service?"*
- **The Trap:** *"Why create the complexity of an Outbox pattern and background scheduler when you can just call `paymentGatewayClient.send()` right after approving?"*
- **Your Defense:**
  > *"Calling external banking APIs synchronously inside a database transaction is an anti-pattern known as the Dual-Write Problem. If the bank API times out after 10 seconds, the database connection is held open, exhausting the connection pool. If the database crashes after the bank API succeeds, the payout is recorded as failed in our DB but money was deducted at the bank. The Transactional Outbox pattern guarantees that the event is committed in the DB first, and dispatched asynchronously with automatic retries and DLQ handling."*

---

### 3. Potential Examiner Trap: *"What if two admins click Approve at the exact same millisecond?"*
- **The Trap:** *"Could two duplicate payouts be triggered if two users concurrently call `/api/payouts/{runId}/finalize`?"*
- **Your Defense:**
  > *"No, because we enforce two independent concurrency defenses:
  > 1. In `PayoutService.finalizeSettlement()`, we use `settlementRunRepository.findByIdWithLock(runId)`, which issues a `SELECT ... FOR UPDATE` row lock in PostgreSQL.
  > 2. The state machine checks `run.getStatus() == RunStatus.APPROVED`. The second concurrent request is blocked at the database lock until the first completes; when unblocked, it finds the run is already in `FINALIZED` status and rejects the call with an `InvalidStateTransitionException`."*

---

### 4. Potential Examiner Trap: *"What happens if the billing server crashes midway during a 10,000 trip calculation?"*
- **The Trap:** *"Will the database have half-calculated runs or corrupted payslips?"*
- **Your Defense:**
  > *"No, because:
  > 1. The calculation executes inside a single `@Transactional` boundary. If an unhandled exception or JVM termination occurs before commit, PostgreSQL rolls back all partial inserts completely.
  > 2. If the process is terminated while in `PROCESSING` status, the background `StaleRunRecoveryTask` identifies runs abandoned past 30 minutes, marks them as `FAILED`, and unblocks subsequent billing runs under our partial unique index."*
