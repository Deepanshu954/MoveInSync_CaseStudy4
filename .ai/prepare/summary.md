# MoveInSync Vendor & Driver Settlement Engine — Executive Summary

## 1. Project Description (2-Minute Elevator Pitch)
The MoveInSync Settlement Engine is an enterprise-grade, high-throughput financial calculation, invoice reconciliation, and dispute resolution platform designed for corporate employee transport logistics. At the end of each billing cycle, it evaluates tens of thousands of completed commute trips, applies time-sliced contract rate cards with mid-month revisions, calculates driver performance incentives, applies capped SLA/safety penalty deductions, and computes statutory Indian Section 194C TDS tax withholding. It enforces mathematical invariants (sum of payslips equals run total net) with **exact 0.00 paise drift**, protects against concurrent double-billing via two-tier idempotency and pessimistic locking, provides asynchronous banking disbursement via the Transactional Outbox pattern with PostgreSQL `SKIP LOCKED`, matches vendor invoices via three-way reconciliation with configurable tolerance, and resolves driver trip disputes via closed-loop, append-only delta settlements while preserving 100% historical ledger immutability.

---

## 2. Technology Stack & Justification

| Technology | Role | Technical Justification |
|---|---|---|
| **Java 21 LTS** | Core Language | Virtual threads readiness, pattern matching, records, sealed types, and superior memory efficiency. |
| **Spring Boot 3.4.x** | Enterprise Framework | Native RFC 7807 problem details, AOP for declarative concerns, and production-ready Actuator observability. |
| **PostgreSQL 16** | Relational Database | ACID guarantees, partial unique indexes for idempotent billing scopes, and `FOR UPDATE SKIP LOCKED` for outbox concurrency. |
| **Pure BigDecimal** | Financial Math | Prevents IEEE-754 floating-point drift; forces explicit `RoundingMode.HALF_UP` with 2 decimal places (exact 0.00 paise drift). |
| **Flyway** | Schema Migrations | Version-controlled, deterministic database evolution (`V1__core_schema.sql`, `V2__seed_data.sql`). |
| **Spring Security + JWT** | Auth & RBAC | Stateless authentication with role-based authorization across 4 distinct personas. |
| **Transactional Outbox** | Distributed Messaging | Eliminates the Dual-Write problem; guarantees at-least-once banking delivery without distributed two-phase commits. |
| **OpenAPI 3.1 / Swagger** | Documentation | Live interactive testing interface with Bearer token authentication at `/swagger-ui.html`. |
| **Docker Compose** | Orchestration | 1-command reproducible local environment bundling PostgreSQL 16 and Spring Boot. |
| **GitHub Actions** | CI Automation | Automated compile, unit test, financial stress test, and packaging verification on every push. |

---

## 3. High-Level Architecture Diagram

```mermaid
graph TD
    Client[Client / Web / Mobile] -->|HTTPS + JWT| Security[Spring Security Filter Chain]
    Security --> TraceFilter[TraceIdFilter: SLF4J MDC X-Trace-Id]
    TraceFilter --> IdempAspect[@Idempotent AOP Aspect: SHA-256 Hash]
    
    IdempAspect --> Controllers[REST Controllers: Settlement, Payout, Reconciliation, Dispute]
    
    subgraph Core Engine Layer
        Controllers --> SettlementSvc[SettlementService]
        SettlementSvc --> CalcEngines[Pure Calculators Layer]
        CalcEngines --> BasePay[BasePayCalculator]
        CalcEngines --> Incentive[IncentiveCalculator]
        CalcEngines --> Penalty[PenaltyCalculator]
        CalcEngines --> Tax[TaxCalculator Section 194C]
    end
    
    subgraph Payout & Resilience Layer
        SettlementSvc --> PayoutSvc[PayoutService]
        PayoutSvc --> InvariantValidator[FinancialInvariantValidator: Zero Drift]
        InvariantValidator --> OutboxTable[(outbox_event Table)]
        OutboxWorker[OutboxProcessorTask: SKIP LOCKED] --> OutboxTable
        OutboxWorker --> BankGateway[MockBankingGateway / Banking Rails]
    end
    
    subgraph Audit & Storage
        SettlementSvc --> Postgres[(PostgreSQL 16 Database)]
        DisputeSvc[DisputeService: Delta Only] --> Postgres
        ReconcileSvc[ReconciliationService: 3-Way Match] --> Postgres
    end
```

---

## 4. Key Capabilities & Architectural Highlights

1. **Exact Mathematical Reconciliation:** Evaluates 10,000 trips across 50 drivers in **under 100 ms** with **0.00 paise drift**.
2. **Two-Tier Idempotency:**
   - *Tier 1 (HTTP Layer):* Intercepts `Idempotency-Key` headers via AOP and returns cached JSON responses for replay requests.
   - *Tier 2 (Domain Scope):* Database partial unique index ensures only one non-failed original settlement run exists per vendor, business unit, and billing period.
3. **Pessimistic State Machine:** Controls payout transitions (`CALCULATED -> UNDER_REVIEW -> APPROVED -> FINALIZED -> PAID`) using `FOR UPDATE` row locks to stop concurrent approval races.
4. **Transactional Outbox with SKIP LOCKED:** Asynchronous banking event dispatch that decouples business transactions from third-party banking latency and network failures.
5. **Three-Way Invoice Matching:** Compares vendor-submitted tax invoices against engine-calculated ledgers with configurable absolute (₹50) and percentage thresholds.
6. **Closed-Loop Dispute Deltas:** Allows drivers/vendors to dispute individual trips, calculating and disbursing strictly the differential amount (Delta = Revised - Original) without modifying historical records.
