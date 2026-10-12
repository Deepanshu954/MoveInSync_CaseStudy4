# Database Schema Reference
## MoveInSync — Vendor & Driver Settlement Engine

All tables are created by Flyway migration `V1__core_schema.sql`. Seed data is loaded by `V2__seed_data.sql`.

---

## Entity Relationship Overview

```
vendor ──────────── business_unit
   │                     │
   ├── contract ──── contract_rate  (versioned, half-open time intervals)
   │
   ├── incentive_policy             (ON_TIME / ATTENDANCE / WORKED_DAYS)
   ├── penalty_policy               (OVER_SPEED / ROUTE_DEVIATION / NIGHT_SAFETY)
   │
   ├── driver ──── app_user (optional login account)
   │      │
   │      └── trip ──── trip_violation
   │
   └── settlement_run
          ├── driver_payslip  (one per driver per run)
          ├── settlement_line (many per driver — granular signed line items)
          ├── settlement_adjustment (append-only dispute delta)
          │
          └── payout_instruction ──── outbox_event

vendor_invoice ──── reconciliation

dispute ──── settlement_adjustment

audit_event     (global append-only audit log)
idempotency_record  (HTTP idempotency key store)
app_user        (authentication accounts)
```

---

## Table Definitions

### `app_user`
Authentication accounts for the settlement system.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `username` | VARCHAR(100) UNIQUE | Login identifier |
| `password_hash` | VARCHAR(255) | BCrypt-12 hash |
| `full_name` | VARCHAR(200) | |
| `email` | VARCHAR(200) UNIQUE | |
| `role` | VARCHAR(30) | `DRIVER`, `VENDOR_ADMIN`, `SETTLEMENT_ADMIN`, `APPROVER` |
| `is_active` | BOOLEAN | Default: `true` |
| `created_at` | TIMESTAMPTZ | |

---

### `vendor`
Transport vendor companies contracted with MoveInSync.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `code` | VARCHAR(50) UNIQUE | Short identifier (e.g. `ACME`) |
| `name` | VARCHAR(200) | |
| `status` | VARCHAR(20) | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| `created_at` | TIMESTAMPTZ | |

---

### `business_unit`
Operational sub-units within a vendor (city/region branches).

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | |
| `code` | VARCHAR(50) UNIQUE | e.g. `ACME-BLR` |
| `name` | VARCHAR(200) | |

---

### `driver`
Individual drivers attached to a vendor.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | |
| `business_unit_id` | BIGINT FK → business_unit | |
| `external_ref` | VARCHAR(100) UNIQUE | External driver ID from fleet system |
| `name` | VARCHAR(200) | |
| `status` | VARCHAR(20) | `ACTIVE`, `INACTIVE` |
| `app_user_id` | BIGINT FK → app_user | nullable — not all drivers have login accounts |

---

### `contract`
Billing contract between MoveInSync and a vendor.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | |
| `business_unit_id` | BIGINT FK → business_unit | nullable |
| `name` | VARCHAR(300) | |
| `currency` | CHAR(3) | Default: `INR` |
| `status` | VARCHAR(20) | `ACTIVE`, `EXPIRED`, `SUSPENDED` |

---

### `contract_rate`
Versioned rate cards for a contract. Rates use half-open time intervals.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `contract_id` | BIGINT FK → contract | |
| `component_type` | VARCHAR(30) | `PER_TRIP` or `PER_DUTY` |
| `amount` | NUMERIC(12,2) | Rupee amount per trip/duty |
| `effective_from` | TIMESTAMPTZ | Inclusive start of rate validity |
| `effective_to` | TIMESTAMPTZ | **Exclusive** end — null means open-ended |
| `version` | INTEGER | Higher version wins on ties |
| `created_at` | TIMESTAMPTZ | |

**How rate resolution works:**
```sql
-- Find the active rate for a trip that completed at 'completedAt'
SELECT * FROM contract_rate
WHERE contract_id = :contractId
  AND component_type = :type
  AND effective_from <= :completedAt
  AND (effective_to IS NULL OR effective_to > :completedAt)
ORDER BY effective_from DESC, version DESC
LIMIT 1;
```

---

### `incentive_policy`
Bonus policies (on-time percentage, attendance, worked days milestones).

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | nullable = applies to all vendors |
| `business_unit_id` | BIGINT FK → business_unit | nullable |
| `type` | VARCHAR(30) | `ON_TIME_BONUS`, `ATTENDANCE_BONUS`, `WORKED_DAYS_BONUS` |
| `amount` | NUMERIC(12,2) | Fixed bonus amount in rupees |
| `threshold_value` | INTEGER | Minimum days worked (for WORKED_DAYS / ATTENDANCE) |
| `threshold_percent` | NUMERIC(5,2) | Minimum on-time % (for ON_TIME_BONUS) |
| `effective_from` | TIMESTAMPTZ | |
| `effective_to` | TIMESTAMPTZ | nullable = open-ended |
| `version` | INTEGER | |

---

### `penalty_policy`
Deduction policies for safety violations.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | nullable = global policy |
| `business_unit_id` | BIGINT FK → business_unit | nullable |
| `violation_type` | VARCHAR(50) | `OVER_SPEED`, `ROUTE_DEVIATION`, `NIGHT_SAFETY_BREACH` |
| `amount` | NUMERIC(12,2) | Deduction per occurrence |
| `frequency_window` | VARCHAR(20) | `DAILY`, `WEEKLY`, `MONTHLY` |
| `max_occurrences` | INTEGER | Max violations charged per window (null = unlimited) |
| `effective_from` | TIMESTAMPTZ | |
| `effective_to` | TIMESTAMPTZ | nullable |
| `version` | INTEGER | |

---

### `tax_config`
Section 194C TDS statutory configuration.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR(200) | e.g. `Standard TDS (10% over 30k)` |
| `monthly_threshold` | NUMERIC(12,2) | Exemption threshold (₹30,000 default) |
| `rate_percent` | NUMERIC(6,4) | Rate — can be stored as fraction (0.1000) or percent (10.00) |
| `effective_from` | TIMESTAMPTZ | |
| `effective_to` | TIMESTAMPTZ | nullable |
| `version` | INTEGER | |

---

### `trip`
Individual commute trips completed by a driver.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `external_ref` | VARCHAR(200) UNIQUE | Trip ID from the fleet/dispatch system |
| `driver_id` | BIGINT FK → driver | |
| `vendor_id` | BIGINT FK → vendor | |
| `business_unit_id` | BIGINT FK → business_unit | |
| `contract_id` | BIGINT FK → contract | |
| `duty_type` | VARCHAR(20) | `TRIP` (point-to-point) or `DUTY` (full-day) |
| `completed_at` | TIMESTAMPTZ | Used to look up which rate was active |
| `is_on_time` | BOOLEAN | Used for incentive eligibility |
| `status` | VARCHAR(20) | `COMPLETED`, `CANCELLED` |

---

### `trip_violation`
Telematics violations recorded during a trip.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `trip_id` | BIGINT FK → trip | |
| `violation_type` | VARCHAR(50) | `OVER_SPEED`, `ROUTE_DEVIATION`, `NIGHT_SAFETY_BREACH` |
| `event_at` | TIMESTAMPTZ | When the violation was detected |
| `source_ref` | VARCHAR(200) | GPS/telematics system reference |
| `status` | VARCHAR(20) | `ACTIVE` (penalised), `DISMISSED` (ignored) |

---

### `settlement_run`
The master record for a single billing period calculation.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | |
| `business_unit_id` | BIGINT FK → business_unit | |
| `period_start` | DATE | Billing cycle start (inclusive) |
| `period_end` | DATE | Billing cycle end (inclusive) |
| `run_type` | VARCHAR(20) | `ORIGINAL` or `ADJUSTMENT` |
| `status` | VARCHAR(30) | `PENDING / PROCESSING / CALCULATED / UNDER_REVIEW / APPROVED / FINALIZED / FAILED` |
| `idempotency_key` | VARCHAR(200) | SHA-256 of the request payload |
| `total_gross` | NUMERIC(15,2) | Sum of all base pay |
| `total_incentives` | NUMERIC(15,2) | Sum of all bonuses |
| `total_penalties` | NUMERIC(15,2) | Sum of all deductions (stored positive) |
| `total_tds` | NUMERIC(15,2) | Sum of all TDS (stored positive) |
| `total_net` | NUMERIC(15,2) | Gross + Incentives − Penalties − TDS |
| `trip_count` | INTEGER | |
| `driver_count` | INTEGER | |
| `calculation_version` | VARCHAR(50) | Engine version at time of calculation |
| `config_hash` | VARCHAR(64) | SHA-256 of rate/policy config snapshot |
| `initiated_by` | VARCHAR(100) | Username |
| `approved_by` | VARCHAR(100) | Username |
| `finalized_by` | VARCHAR(100) | Username |
| `error_message` | TEXT | Populated on FAILED |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**Critical index:**
```sql
CREATE UNIQUE INDEX idx_settlement_run_original_scope
    ON settlement_run(vendor_id, business_unit_id, period_start, period_end)
    WHERE run_type = 'ORIGINAL' AND status != 'FAILED';
```
This partial index prevents duplicate active ORIGINAL runs at the database level.

---

### `driver_payslip`
Aggregated financial summary per driver per settlement run.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `settlement_run_id` | BIGINT FK → settlement_run | |
| `driver_id` | BIGINT FK → driver | |
| `gross_pay` | NUMERIC(15,2) | |
| `incentives` | NUMERIC(15,2) | |
| `penalties` | NUMERIC(15,2) | (stored positive) |
| `tds_deducted` | NUMERIC(15,2) | (stored positive) |
| `net_pay` | NUMERIC(15,2) | |
| `trip_count` | INTEGER | |

---

### `settlement_line`
Granular signed line items making up each payslip.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `settlement_run_id` | BIGINT FK → settlement_run | |
| `driver_id` | BIGINT FK → driver | |
| `line_type` | VARCHAR(30) | `BASE_PAY`, `INCENTIVE`, `PENALTY`, `TDS` |
| `reference_type` | VARCHAR(50) | Type of referenced entity (e.g. `TRIP`) |
| `reference_id` | BIGINT | ID of referenced entity |
| `description` | VARCHAR(500) | Human-readable description |
| `amount` | NUMERIC(15,2) | **Signed** — credits positive, debits negative |
| `rate_version` | INTEGER | Version of rate card used |
| `policy_version` | INTEGER | Version of policy used |
| `calculation_details` | JSONB | Full audit trail (tripRef, rateId, rateAmount, etc.) |

---

### `vendor_invoice`
Invoice submitted by the vendor at month end.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `vendor_id` | BIGINT FK → vendor | |
| `business_unit_id` | BIGINT FK → business_unit | |
| `invoice_ref` | VARCHAR(200) UNIQUE | Vendor's own invoice number |
| `amount` | NUMERIC(15,2) | Vendor-claimed amount |
| `period_start` | DATE | |
| `period_end` | DATE | |
| `submitted_at` | TIMESTAMPTZ | |

---

### `reconciliation`
Result of comparing a vendor invoice against a settlement run.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `invoice_id` | BIGINT FK → vendor_invoice | |
| `settlement_run_id` | BIGINT FK → settlement_run | |
| `invoice_amount` | NUMERIC(15,2) | |
| `calculated_amount` | NUMERIC(15,2) | From the settlement run |
| `delta` | NUMERIC(15,2) | invoice − calculated |
| `tolerance_amount` | NUMERIC(15,2) | Configured tolerance |
| `status` | VARCHAR(30) | `MATCHED` or `DISCREPANCY` |
| `message` | TEXT | Human-readable explanation |
| `reconciled_at` | TIMESTAMPTZ | |

---

### `dispute`
A billing dispute raised by a driver or vendor for a specific trip.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `trip_id` | BIGINT FK → trip | |
| `settlement_run_id` | BIGINT FK → settlement_run | |
| `reason` | TEXT | |
| `evidence_ref` | VARCHAR(500) | Reference to supporting document |
| `status` | VARCHAR(20) | `OPEN`, `APPROVED`, `REJECTED` |
| `resolution_comments` | TEXT | |
| `raised_by` | VARCHAR(100) | Username |
| `resolved_by` | VARCHAR(100) | Username |
| `raised_at` | TIMESTAMPTZ | |
| `resolved_at` | TIMESTAMPTZ | |

---

### `settlement_adjustment`
Append-only delta record created when a dispute is approved.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `dispute_id` | BIGINT FK → dispute | |
| `settlement_run_id` | BIGINT FK → settlement_run | original run |
| `driver_id` | BIGINT FK → driver | |
| `original_amount` | NUMERIC(15,2) | What was originally paid |
| `revised_amount` | NUMERIC(15,2) | What should have been paid |
| `delta_amount` | NUMERIC(15,2) | revised − original |
| `adjustment_type` | VARCHAR(30) | `DISPUTE_APPROVED` |
| `payout_instruction_id` | BIGINT FK → payout_instruction | delta payout |
| `created_at` | TIMESTAMPTZ | |

---

### `idempotency_record`
HTTP-layer idempotency key tracking.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `idempotency_key` | VARCHAR(500) UNIQUE | From `Idempotency-Key` header |
| `request_hash` | VARCHAR(64) | SHA-256 of request body |
| `response_body` | TEXT | Cached response JSON |
| `status` | VARCHAR(20) | `PROCESSING`, `COMPLETED`, `FAILED` |
| `expires_at` | TIMESTAMPTZ | 24h TTL |
| `created_at` | TIMESTAMPTZ | |

---

### `payout_instruction`
Banking disbursement instruction for a single driver.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `settlement_run_id` | BIGINT FK → settlement_run | |
| `driver_id` | BIGINT FK → driver | |
| `amount` | NUMERIC(15,2) | |
| `payment_mode` | VARCHAR(20) | `NEFT`, `IMPS`, `UPI` |
| `bank_account_ref` | VARCHAR(200) | |
| `status` | VARCHAR(20) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `utr_reference` | VARCHAR(200) | Bank UTR number (filled on success) |
| `failure_reason` | TEXT | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### `outbox_event`
Transactional Outbox events awaiting dispatch.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `event_type` | VARCHAR(100) | `SETTLEMENT_FINALIZED`, `PAYOUT_INITIATED`, `PAYOUT_DELTA` |
| `aggregate_type` | VARCHAR(100) | `SettlementRun`, `PayoutInstruction` |
| `aggregate_id` | BIGINT | |
| `payload` | TEXT | JSON event payload |
| `status` | VARCHAR(20) | `PENDING`, `SENT`, `FAILED` |
| `retry_count` | INTEGER | Default: 0 |
| `created_at` | TIMESTAMPTZ | |
| `processed_at` | TIMESTAMPTZ | |

**Key index for outbox polling:**
```sql
CREATE INDEX idx_outbox_event_pending
    ON outbox_event(created_at)
    WHERE status = 'PENDING';
```

---

### `audit_event`
Immutable, append-only audit log. Records are never updated or deleted.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `actor` | VARCHAR(200) | Username or `SYSTEM` |
| `action` | VARCHAR(200) | e.g. `SETTLEMENT_CALCULATED`, `DISPUTE_RESOLVED` |
| `entity_type` | VARCHAR(100) | e.g. `SettlementRun` |
| `entity_id` | BIGINT | |
| `correlation_id` | VARCHAR(200) | Links to `X-Trace-Id` |
| `details` | TEXT | JSON snapshot of the result |
| `created_at` | TIMESTAMPTZ | |
