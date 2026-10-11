# MoveInSync Settlement Engine — Module-by-Module Guide

This document provides a breakdown of each core package, its design patterns, and how it interacts across the platform.

---

## 1. `com.moveinsync.settlement.calculation` (Pure Financial Math Engine)
- **Role:** Implements domain arithmetic for trip compensation, incentives, deductions, taxes, tolerances, and delta adjustments.
- **Key Files:**
  - `BasePayCalculator.java`: Matches trips against time-sliced `ContractRate` intervals `[effectiveFrom, effectiveTo)` with version precedence.
  - `IncentiveCalculator.java`: Evaluates operational bonuses (`ON_TIME_BONUS`, `WORKED_DAYS_BONUS`, `ATTENDANCE_BONUS`).
  - `PenaltyCalculator.java`: Partitions safety infractions into frequency buckets (`DAILY`, `WEEKLY`, `MONTHLY`) and enforces occurrence capping.
  - `TaxCalculator.java`: Implements Indian Income Tax Act Section 194C statutory TDS (1% individual, 2% company above ₹30,000 threshold).
  - `PayoutCalculator.java`: Verifies zero mathematical drift between sum of payslips and run aggregate net.
  - `ReconciliationCalculator.java`: Computes invoice variance and tests against absolute/percentage tolerance limits.
  - `DisputeDeltaCalculator.java`: Evaluates closed-loop differential payouts (`Delta = Revised - Original`).
- **Design Pattern:** Strategy Pattern & Pure Functions (Stateless, zero DB queries, 100% testable).

---

## 2. `com.moveinsync.settlement.service` (Domain Orchestration)
- **Role:** Manages business transactions, entity lifecycle state transitions, database coordination, and auditing.
- **Key Files:**
  - `SettlementService.java`: Batch calculation orchestrator with preview mode and N+1 query elimination via `JOIN FETCH`.
  - `PayoutService.java`: Payout instruction queries and banking webhook processing.
  - `ReconciliationService.java`: Three-way invoice matching and variance recording.
  - `DisputeService.java`: Ticketing and immutable delta adjustments.
  - `PolicyCacheService.java`: Spring `@Cacheable` caching layer for contract rates and rules.
  - `IdempotencyService.java`: Cryptographic SHA-256 payload hashing and concurrent lock handling.

---

## 3. `com.moveinsync.settlement.payout` (Asynchronous Banking & Outbox)
- **Role:** Handles resilient, asynchronous bank disbursements and background scheduling.
- **Key Files:**
  - `payout/task/OutboxProcessorTask.java`: Polls `outbox_event` table using PostgreSQL `SKIP LOCKED`.
  - `payout/task/OutboxEventDispatcher.java`: Dispatches individual events within isolated transactions (`REQUIRES_NEW`) with max-retry DLQ handling.
  - `payout/task/StaleRunRecoveryTask.java`: Automatically transitions abandoned `PROCESSING` runs past 30 minutes to `FAILED`.
  - `payout/gateway/MockBankingGateway.java`: Simulates banking API transfers with unique UTR reference generation.

---

## 4. `com.moveinsync.settlement.common` (Cross-Cutting Concerns)
- **Role:** Enterprise infrastructure, observability, exception handling, and AOP.
- **Key Files:**
  - `common/filter/TraceIdFilter.java`: Extracts or generates `X-Trace-Id` and binds to SLF4J MDC context.
  - `common/exception/GlobalExceptionHandler.java`: RFC 7807 compliant problem details with trace correlation.
  - `common/aspect/IdempotencyAspect.java`: Transparent declarative AOP HTTP idempotency.
  - `common/aspect/AuditAspect.java`: Audits critical business actions to `audit_event`.
  - `common/metrics/SettlementMetrics.java`: Micrometer business counters exposed at `/actuator/metrics`.
