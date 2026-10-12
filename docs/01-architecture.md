# Architecture & System Design
## MoveInSync — Vendor & Driver Settlement Engine

---

## 1. System Overview

The Settlement Engine is a **stateless, domain-driven REST microservice** responsible for end-to-end financial settlement between MoveInSync and its transport vendors.

At the end of each billing cycle, it:
1. Calculates base pay, incentives, penalties, and TDS for every driver
2. Routes the settlement run through a multi-step approval workflow
3. Generates and dispatches banking payout instructions
4. Reconciles vendor-submitted invoices against calculated totals
5. Handles disputed trips with immutable, append-only delta adjustments

**Core financial formula:**
```
Net Payable = Base Pay + Incentives − Penalties − TDS (Sec. 194C)
```

---

## 2. Architecture Layers

```
┌──────────────────────────────────────────────────────────────────────┐
│                        HTTP / REST Layer                              │
│   /api/auth  │  /api/settlements  │  /api/payouts  │  /api/disputes  │
│              │  /api/reconciliations                                  │
│              JWT filter → role check before every handler             │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────────┐
│                     Service / Orchestration Layer                     │
│  SettlementService    PayoutService    ReconciliationService          │
│  DisputeService       IdempotencyService   PolicyCacheService         │
│                                                                       │
│  Key responsibilities:                                                │
│  • Coordinate calculator calls                                        │
│  • Enforce state machine transitions (pessimistic lock)               │
│  • Drive idempotency checks (two-tier)                                │
│  • Write to DB and outbox in single transaction                       │
└───────┬─────────────────────────┬────────────────────┬───────────────┘
        │                         │                     │
┌───────▼──────────┐   ┌──────────▼──────────┐  ┌──────▼──────────────┐
│  Financial       │   │  PostgreSQL 16        │  │ Background Tasks    │
│  Calculators     │   │  (22 tables)          │  │                     │
│  (pure, no DB)   │   │  Flyway migrations    │  │ OutboxProcessorTask │
│  ─────────────── │   │  SKIP LOCKED          │  │   polls every 5s    │
│  BasePayCalc     │   │  SELECT FOR UPDATE    │  │   SKIP LOCKED       │
│  IncentiveCalc   │   │  Partial indexes      │  │                     │
│  PenaltyCalc     │   │  JSONB audit cols     │  │ StaleRunRecovery    │
│  TaxCalculator   │   └───────────────────────┘  │   polls every 15m  │
└──────────────────┘                               └─────────────────────┘
```

### Design Principle: Calculators Are Pure Functions

The four financial calculators (`BasePayCalculator`, `IncentiveCalculator`, `PenaltyCalculator`, `TaxCalculator`) are deliberately kept **free of all Spring and database dependencies**. They receive plain Java objects (entities) and return `List<CalculatedLineItem>`. This means:

- They can be unit-tested with zero infrastructure (no DB, no Spring context)
- They can be run inside a preview/dry-run without any DB side effects
- The 10,000-trip stress test runs entirely in-memory in < 200 ms

---

## 3. Settlement Lifecycle State Machine

```
  ┌─────────┐
  │ PENDING │  (run record created, calculation not yet started)
  └────┬────┘
       │ calculation completes
       ▼
  ┌────────────┐
  │ CALCULATED │  (all payslips and line items persisted)
  └────┬───────┘
       │ optional finance review
       ▼
  ┌──────────────┐
  │ UNDER_REVIEW │  (finance team verifying figures)
  └─────┬────────┘
        │ finance approves
        ▼
  ┌──────────┐
  │ APPROVED │  (ready for payout disbursement)
  └────┬─────┘
       │ finalize (FinancialInvariantValidator passes)
       ▼
  ┌───────────┐     ┌─────────────────────────────────────────────────┐
  │ FINALIZED │────►│ PayoutInstruction (one per driver, same txn)    │
  └───────────┘     │ OutboxEvent (SETTLEMENT_FINALIZED, PAYOUT_INIT) │
                    └─────────────────────────────────────────────────┘

  Any state → FAILED  (on crash / FinancialInvariantValidator failure)
```

**State transitions are enforced via pessimistic locking (`SELECT FOR UPDATE`) to prevent concurrent races.**

---

## 4. Two-Tier Idempotency

Settlement calculation must be idempotent — running the same billing period twice must return the same result, not create duplicate payslips.

### Tier 1 — HTTP Layer (IdempotencyService)

```
Client sends:  POST /api/settlements/calculate
               Idempotency-Key: ACME-BLR-OCT-2026
               X-Trace-Id: abc-123

IdempotencyService:
  1. Hash the request body → SHA-256 fingerprint
  2. Look up the Idempotency-Key in idempotency_record table
     - Not found: insert record with status=PROCESSING, proceed
     - COMPLETED: return cached response (no re-calculation)
     - PROCESSING: return 409 (in-flight, try again)
     - Hash mismatch: return 409 (same key, different payload)
```

### Tier 2 — Database Layer (Partial Unique Index)

```sql
CREATE UNIQUE INDEX idx_settlement_run_original_scope
    ON settlement_run(vendor_id, business_unit_id, period_start, period_end)
    WHERE run_type = 'ORIGINAL' AND status != 'FAILED';
```

Even if two requests bypass Tier 1 simultaneously, the database will reject the second `INSERT` with a constraint violation. The service layer catches this and returns the existing run.

---

## 5. Transactional Outbox Pattern

When a settlement run is finalized, the system must dispatch banking payout instructions. The naive approach (calling a bank API inside the transaction) creates distributed transaction problems — the DB write could succeed but the API call could fail.

**Solution: Transactional Outbox**

```
SettlementService.finalizeSettlementRun():
  BEGIN TRANSACTION
    1. settlement_run.status = FINALIZED
    2. INSERT payout_instruction (one per driver)
    3. INSERT outbox_event (SETTLEMENT_FINALIZED, PAYOUT_INITIATED)
  COMMIT  ← all or nothing

OutboxProcessorTask (every 5 seconds):
  SELECT * FROM outbox_event
  WHERE status = 'PENDING'
  LIMIT 50
  FOR UPDATE SKIP LOCKED;         ← prevents race in multi-instance

  For each event:
    BEGIN TRANSACTION
      call MockBankingGateway
      outbox_event.status = SENT (or FAILED + retry_count++)
    COMMIT
```

`SKIP LOCKED` means multiple instances of the application can poll the outbox concurrently without stepping on each other — no Kafka or Redis needed.

---

## 6. Stale Run Crash Recovery

If the JVM crashes during a `PROCESSING` settlement run, the partial unique index would permanently block new billing runs for that period (because `status != 'FAILED'` would still be true for the stuck `PROCESSING` run).

`StaleRunRecoveryTask` runs every 15 minutes and finds runs that have been in `PROCESSING` status for more than 30 minutes. It marks them `FAILED`, writes an audit event, and releases the unique index.

```
StaleRunRecoveryTask (every 15 min):
  cutoff = now() - 30 minutes
  stale = SELECT * FROM settlement_run
          WHERE status = 'PROCESSING' AND updated_at < cutoff

  For each stale run:
    run.status = FAILED
    INSERT audit_event (STALE_RUN_RECOVERED_TO_FAILED)
```

---

## 7. AOP-Driven Cross-Cutting Concerns

Three custom annotations drive cross-cutting behaviour via Spring AOP:

| Annotation | Aspect | Effect |
|---|---|---|
| `@Audited(action, entityType)` | `AuditAspect` | Saves immutable `AuditEvent` after method returns. Extracts actor from Spring Security context. |
| `@ExecutionTime` | `ExecutionTimeAspect` | Logs method name and execution duration in milliseconds. |
| `@Idempotent` | `IdempotencyAspect` | Declarative idempotency enforcement via the `Idempotency-Key` header. |

---

## 8. Security Model

```
POST /api/v1/auth/login  →  returns JWT (24h expiry, BCrypt-12 password)

Every other request:
  JwtAuthenticationFilter.doFilterInternal()
    → validates signature + expiry
    → loads UserDetails from DB
    → sets SecurityContextHolder

@PreAuthorize annotations on service methods enforce role checks:
  DRIVER           — own payslips, raise disputes
  VENDOR_ADMIN     — invoices, reconciliations
  SETTLEMENT_ADMIN — full settlement management
  APPROVER         — approve + finalize runs
```

---

## 9. Technology Choices

| Choice | Alternative Considered | Rationale |
|---|---|---|
| PostgreSQL `SKIP LOCKED` for outbox | Kafka / RabbitMQ | No extra infra. Simpler ops. Sufficient for case study scale. |
| Pure-function calculators | Business logic inside service | Enables pure unit testing and preview mode without any DB. |
| Two-tier idempotency (header + DB index) | Header-only | DB partial index catches races that survive the HTTP tier. |
| `BigDecimal` + `HALF_UP` everywhere | `double` | Eliminates all floating-point rounding errors in currency math. |
| `SELECT FOR UPDATE` on state transitions | Optimistic locking | Settlement approval is rare but high-stakes; pessimistic is safer. |
| Append-only disputes | Mutating historical lines | Preserves complete immutable audit trail. |
| Flyway | Liquibase | Simpler config. SQL-first. Wide Spring Boot support. |
