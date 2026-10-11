# Project Package Restructuring Guide: MoveInSync Settlement Engine

> **Purpose:** This document presents a comprehensive, 1-to-1 mapping of **every file** in the project across three structural models:
> 1. **Current Structure** (16 fragmented packages, 1–2 files each)
> 2. **Option A: Cohesive Bounded Contexts** (4 clean domain-focused packages)
> 3. **Option B: Classic Layered Architecture** (Layer-by-layer: `entity`, `repository`, `security`, `common`)

---

## 1. Visual Comparison of Directory Trees

### Current Structure (16 Micro-Packages ❌)
```
com.moveinsync.settlement/
├── audit/           (2 files: entity + repo)
├── auth/            (7 files: entity + repo + controller + service + dtos)
├── common/          (9 files: exceptions + config)
├── contract/        (4 files: 2 entities + 2 repos)
├── dispute/         (2 files: entity + repo)
├── driver/          (2 files: entity + repo)
├── idempotency/     (2 files: entity + repo)
├── invoice/         (2 files: entity + repo)
├── outbox/          (2 files: entity + repo)
├── payout/          (2 files: entity + repo)
├── policy/          (6 files: 3 entities + 3 repos)
├── reconciliation/  (2 files: entity + repo)
├── security/        (7 files: jwt + filter + config)
├── settlement/      (8 files: 4 entities + 4 repos)
├── trip/            (4 files: 2 entities + 2 repos)
└── vendor/          (4 files: 2 entities + 2 repos)
```

---

### Option A: Cohesive Bounded Contexts (Recommended — 4 Packages ✅)
*Groups by cohesive business context. Eliminates tiny 1-entity folders by placing operational master data together, unifying security, and isolating settlement engine core.*

```
com.moveinsync.settlement/
├── SettlementEngineApplication.java
│
├── auth/               -> All Identity, Security & JWT (12 files)
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   ├── security/       (JwtService, SecurityConfig, Filters, EntryPoint)
│   └── service/
│
├── domain/             -> All Core & Master Operational Data (32 files)
│   ├── entity/         (Vendor, Driver, Contract, Trip, Policy, Invoice, Dispute, etc.)
│   └── repository/     (Matching Spring Data repositories)
│
├── settlement/         -> Pure Settlement Core & Calculations (8 files now, calculators in Stage 2)
│   ├── entity/         (SettlementRun, SettlementLine, DriverPayslip, SettlementAdjustment)
│   ├── repository/     (Matching repositories)
│   └── calculator/     (Ready for Stage 2 pure math engines)
│
└── shared/             -> Cross-Cutting Infrastructure & Platform (14 files)
    ├── config/         (JacksonConfig)
    ├── exception/      (GlobalExceptionHandler, Exception DTOs, Custom Exceptions)
    ├── audit/          (AuditEvent, AuditEventRepository)
    ├── outbox/         (OutboxEvent, OutboxEventRepository)
    └── idempotency/    (IdempotencyRecord, IdempotencyRecordRepository)
```

---

### Option B: Classic Layered Architecture (CoderArmy Standard — 4 Packages ✅)
*Standard Spring Boot 3-Tier Layering. Zero guesswork on where files live. All entities together, all repositories together.*

```
com.moveinsync.settlement/
├── SettlementEngineApplication.java
│
├── entity/             -> All 20 JPA Entities in one place
│   ├── AppUser.java, Role.java
│   ├── Vendor.java, BusinessUnit.java, Driver.java
│   ├── Contract.java, ContractRate.java
│   ├── Trip.java, TripViolation.java
│   ├── IncentivePolicy.java, PenaltyPolicy.java, TaxConfig.java
│   ├── SettlementRun.java, SettlementLine.java, DriverPayslip.java, SettlementAdjustment.java
│   ├── VendorInvoice.java, ReconciliationRecord.java, Dispute.java, PayoutInstruction.java
│   └── OutboxEvent.java, AuditEvent.java, IdempotencyRecord.java
│
├── repository/         -> All 22 Spring Data JPA Repositories in one place
│   ├── UserRepository.java
│   ├── VendorRepository.java, BusinessUnitRepository.java, DriverRepository.java
│   ├── ContractRepository.java, ContractRateRepository.java
│   ├── TripRepository.java, TripViolationRepository.java
│   ├── IncentivePolicyRepository.java, PenaltyPolicyRepository.java, TaxConfigRepository.java
│   ├── SettlementRunRepository.java, SettlementLineRepository.java, DriverPayslipRepository.java, SettlementAdjustmentRepository.java
│   ├── VendorInvoiceRepository.java, ReconciliationRecordRepository.java, DisputeRepository.java, PayoutInstructionRepository.java
│   └── OutboxEventRepository.java, AuditEventRepository.java, IdempotencyRecordRepository.java
│
├── security/           -> Authentication, JWT, and Security Filter Chain (11 files)
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── AuthRequestDto.java, AuthResponseDto.java
│   ├── SecurityConfig.java
│   ├── JwtService.java, JwtAuthenticationFilter.java
│   ├── JwtAuthenticationEntryPoint.java, CustomAccessDeniedHandler.java
│   └── CustomUserDetails.java, CustomUserDetailsService.java
│
└── common/             -> Exceptions and Global Configuration (10 files)
    ├── config/JacksonConfig.java
    ├── exception/GlobalExceptionHandler.java
    ├── exception/dto/ExceptionResponseDto.java
    ├── exception/dto/ValidationExceptionResponseDto.java
    └── exception/*.java (6 custom domain exceptions)
```

---

## 2. Exhaustive File-by-File Mapping

| # | File Name | Current Path | Option A (Bounded Contexts) | Option B (Classic Layered) |
|---|---|---|---|---|
| 1 | `SettlementEngineApplication.java` | `com.moveinsync.settlement` | `com.moveinsync.settlement` | `com.moveinsync.settlement` |
| **Authentication & Security** | | | | |
| 2 | `AppUser.java` | `auth.entity` | `auth.entity` | `entity` |
| 3 | `Role.java` | `auth.entity` | `auth.entity` | `entity` |
| 4 | `UserRepository.java` | `auth.repository` | `auth.repository` | `repository` |
| 5 | `CustomUserDetails.java` | `security` | `auth.security` | `security` |
| 6 | `CustomUserDetailsService.java` | `security` | `auth.security` | `security` |
| 7 | `JwtService.java` | `security` | `auth.security` | `security` |
| 8 | `JwtAuthenticationFilter.java` | `security` | `auth.security` | `security` |
| 9 | `JwtAuthenticationEntryPoint.java` | `security` | `auth.security` | `security` |
| 10 | `CustomAccessDeniedHandler.java` | `security` | `auth.security` | `security` |
| 11 | `SecurityConfig.java` | `security` | `auth.security` | `security` |
| 12 | `AuthController.java` | `auth.controller` | `auth.controller` | `security` (or `controller`) |
| 13 | `AuthService.java` | `auth.service` | `auth.service` | `security` (or `service`) |
| 14 | `AuthRequestDto.java` | `auth.dto` | `auth.dto` | `security` (or `dto`) |
| 15 | `AuthResponseDto.java` | `auth.dto` | `auth.dto` | `security` (or `dto`) |
| **Vendor & Driver Master Data** | | | | |
| 16 | `Vendor.java` | `vendor.entity` | `domain.entity` | `entity` |
| 17 | `BusinessUnit.java` | `vendor.entity` | `domain.entity` | `entity` |
| 18 | `VendorRepository.java` | `vendor.repository` | `domain.repository` | `repository` |
| 19 | `BusinessUnitRepository.java` | `vendor.repository` | `domain.repository` | `repository` |
| 20 | `Driver.java` | `driver.entity` | `domain.entity` | `entity` |
| 21 | `DriverRepository.java` | `driver.repository` | `domain.repository` | `repository` |
| **Contracts & Rates** | | | | |
| 22 | `Contract.java` | `contract.entity` | `domain.entity` | `entity` |
| 23 | `ContractRate.java` | `contract.entity` | `domain.entity` | `entity` |
| 24 | `ContractRepository.java` | `contract.repository` | `domain.repository` | `repository` |
| 25 | `ContractRateRepository.java` | `contract.repository` | `domain.repository` | `repository` |
| **Trips & Violations** | | | | |
| 26 | `Trip.java` | `trip.entity` | `domain.entity` | `entity` |
| 27 | `TripViolation.java` | `trip.entity` | `domain.entity` | `entity` |
| 28 | `TripRepository.java` | `trip.repository` | `domain.repository` | `repository` |
| 29 | `TripViolationRepository.java` | `trip.repository` | `domain.repository` | `repository` |
| **Policies (Incentive, Penalty, Tax)** | | | | |
| 30 | `IncentivePolicy.java` | `policy.entity` | `domain.entity` | `entity` |
| 31 | `PenaltyPolicy.java` | `policy.entity` | `domain.entity` | `entity` |
| 32 | `TaxConfig.java` | `policy.entity` | `domain.entity` | `entity` |
| 33 | `IncentivePolicyRepository.java` | `policy.repository` | `domain.repository` | `repository` |
| 34 | `PenaltyPolicyRepository.java` | `policy.repository` | `domain.repository` | `repository` |
| 35 | `TaxConfigRepository.java` | `policy.repository` | `domain.repository` | `repository` |
| **Settlement Core** | | | | |
| 36 | `SettlementRun.java` | `settlement.entity` | `settlement.entity` | `entity` |
| 37 | `SettlementLine.java` | `settlement.entity` | `settlement.entity` | `entity` |
| 38 | `DriverPayslip.java` | `settlement.entity` | `settlement.entity` | `entity` |
| 39 | `SettlementAdjustment.java` | `settlement.entity` | `settlement.entity` | `entity` |
| 40 | `SettlementRunRepository.java` | `settlement.repository` | `settlement.repository` | `repository` |
| 41 | `SettlementLineRepository.java` | `settlement.repository` | `settlement.repository` | `repository` |
| 42 | `DriverPayslipRepository.java` | `settlement.repository` | `settlement.repository` | `repository` |
| 43 | `SettlementAdjustmentRepository.java` | `settlement.repository` | `settlement.repository` | `repository` |
| **Invoicing, Reconciliation, Disputes, Payouts** | | | | |
| 44 | `VendorInvoice.java` | `invoice.entity` | `domain.entity` | `entity` |
| 45 | `VendorInvoiceRepository.java` | `invoice.repository` | `domain.repository` | `repository` |
| 46 | `ReconciliationRecord.java` | `reconciliation.entity` | `domain.entity` | `entity` |
| 47 | `ReconciliationRecordRepository.java` | `reconciliation.repository` | `domain.repository` | `repository` |
| 48 | `Dispute.java` | `dispute.entity` | `domain.entity` | `entity` |
| 49 | `DisputeRepository.java` | `dispute.repository` | `domain.repository` | `repository` |
| 50 | `PayoutInstruction.java` | `payout.entity` | `domain.entity` | `entity` |
| 51 | `PayoutInstructionRepository.java` | `payout.repository` | `domain.repository` | `repository` |
| **Platform Infrastructure (Outbox, Audit, Idempotency)** | | | | |
| 52 | `OutboxEvent.java` | `outbox.entity` | `shared.outbox` | `entity` |
| 53 | `OutboxEventRepository.java` | `outbox.repository` | `shared.outbox` | `repository` |
| 54 | `AuditEvent.java` | `audit.entity` | `shared.audit` | `entity` |
| 55 | `AuditEventRepository.java` | `audit.repository` | `shared.audit` | `repository` |
| 56 | `IdempotencyRecord.java` | `idempotency.entity` | `shared.idempotency` | `entity` |
| 57 | `IdempotencyRecordRepository.java` | `idempotency.repository` | `shared.idempotency` | `repository` |
| **Common Configuration & Exceptions** | | | | |
| 58 | `JacksonConfig.java` | `common.config` | `shared.config` | `common.config` |
| 59 | `GlobalExceptionHandler.java` | `common.exception` | `shared.exception` | `common.exception` |
| 60 | `ExceptionResponseDto.java` | `common.exception.dto` | `shared.exception.dto` | `common.exception.dto` |
| 61 | `ValidationExceptionResponseDto.java` | `common.exception.dto` | `shared.exception.dto` | `common.exception.dto` |
| 62 | `ResourceNotFoundException.java` | `common.exception` | `shared.exception` | `common.exception` |
| 63 | `DuplicateResourceException.java` | `common.exception` | `shared.exception` | `common.exception` |
| 64 | `IdempotencyConflictException.java` | `common.exception` | `shared.exception` | `common.exception` |
| 65 | `InvalidStateTransitionException.java` | `common.exception` | `shared.exception` | `common.exception` |
| 66 | `PolicyViolationException.java` | `common.exception` | `shared.exception` | `common.exception` |

---

## 3. Comparison & Trade-Off Analysis

| Criteria | Option A (Cohesive Domains) | Option B (Classic Layered) |
|---|---|---|
| **Folder Count** | **4 top packages** (`auth`, `domain`, `settlement`, `shared`) | **4 top packages** (`entity`, `repository`, `security`, `common`) |
| **Mental Model** | Domain Driven: Related business logic stays near its domain | Technical Layer: All files of the same type live together |
| **Package Circular Dependencies** | Zero. `settlement` depends on `domain`, `auth` is isolated. | Zero. Repositories depend on entities, services depend on repositories. |
| **IDE Navigation Speed** | Very fast: 4 clean folders | Fastest: 1 click to any entity or repo |
| **Scalability (Stages 2 to 7)** | High: Calculators neatly slot into `settlement.calculator`, payout services into `domain.service` | High: Simply add `calculator/`, `service/`, `controller/` when needed |
| **Verdict** | **Best for domain clarity & enterprise systems** | **Simplest & most intuitive for rapid development** |
