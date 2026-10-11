-- ============================================================
-- V1__core_schema.sql
-- MoveInSync Case Study 4: Production Core Schema
-- ============================================================

-- 1. APP USER & AUTHENTICATION
CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    full_name       VARCHAR(200)  NOT NULL,
    email           VARCHAR(200),
    role            VARCHAR(50)   NOT NULL CHECK (role IN ('DRIVER', 'VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'APPROVER')),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. VENDOR & BUSINESS UNIT
CREATE TABLE vendor (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL UNIQUE,
    name            VARCHAR(200)  NOT NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE business_unit (
    id              BIGSERIAL PRIMARY KEY,
    vendor_id       BIGINT        NOT NULL REFERENCES vendor(id),
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (vendor_id, code)
);

CREATE INDEX idx_bu_vendor ON business_unit(vendor_id);

-- 3. DRIVER
CREATE TABLE driver (
    id                  BIGSERIAL PRIMARY KEY,
    vendor_id           BIGINT        NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT        NOT NULL REFERENCES business_unit(id),
    external_ref        VARCHAR(100)  NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    app_user_id         BIGINT        REFERENCES app_user(id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (vendor_id, external_ref)
);

CREATE INDEX idx_driver_vendor ON driver(vendor_id);
CREATE INDEX idx_driver_bu ON driver(business_unit_id);

-- 4. CONTRACT & VERSIONED RATES (Half-open interval)
CREATE TABLE contract (
    id                  BIGSERIAL PRIMARY KEY,
    vendor_id           BIGINT        NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT        REFERENCES business_unit(id),
    name                VARCHAR(200)  NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contract_vendor ON contract(vendor_id);

CREATE TABLE contract_rate (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT        NOT NULL REFERENCES contract(id),
    component_type      VARCHAR(30)   NOT NULL CHECK (component_type IN ('PER_TRIP', 'PER_DUTY')),
    amount              NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    effective_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to        TIMESTAMP WITH TIME ZONE,
    version             INTEGER       NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rate_contract_effective ON contract_rate(contract_id, component_type, effective_from);

-- 5. POLICIES (Incentives, Penalties, Tax Config)
CREATE TABLE incentive_policy (
    id                  BIGSERIAL PRIMARY KEY,
    vendor_id           BIGINT        NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT        REFERENCES business_unit(id),
    type                VARCHAR(50)   NOT NULL CHECK (type IN ('ON_TIME_BONUS', 'ATTENDANCE_BONUS', 'WORKED_DAYS_BONUS')),
    amount              NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    threshold_value     INTEGER,
    threshold_percent   NUMERIC(5, 2),
    effective_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to        TIMESTAMP WITH TIME ZONE,
    version             INTEGER       NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inc_policy_vendor ON incentive_policy(vendor_id, business_unit_id);

CREATE TABLE penalty_policy (
    id                  BIGSERIAL PRIMARY KEY,
    vendor_id           BIGINT        NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT        REFERENCES business_unit(id),
    violation_type      VARCHAR(50)   NOT NULL CHECK (violation_type IN ('OVER_SPEED', 'ROUTE_DEVIATION', 'NIGHT_SAFETY_BREACH')),
    amount              NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    frequency_window    VARCHAR(20)   NOT NULL CHECK (frequency_window IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    max_occurrences     INTEGER,
    effective_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to        TIMESTAMP WITH TIME ZONE,
    version             INTEGER       NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pen_policy_vendor ON penalty_policy(vendor_id, business_unit_id);

CREATE TABLE tax_config (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100)  NOT NULL,
    monthly_threshold   NUMERIC(15, 2) NOT NULL CHECK (monthly_threshold >= 0),
    rate_percent        NUMERIC(5, 4) NOT NULL CHECK (rate_percent >= 0 AND rate_percent <= 1),
    effective_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to        TIMESTAMP WITH TIME ZONE,
    version             INTEGER       NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 6. TRIP & TELEMATICS VIOLATIONS
CREATE TABLE trip (
    id                  BIGSERIAL PRIMARY KEY,
    external_ref        VARCHAR(100)  NOT NULL,
    driver_id           BIGINT        NOT NULL REFERENCES driver(id),
    vendor_id           BIGINT        NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT        NOT NULL REFERENCES business_unit(id),
    contract_id         BIGINT        NOT NULL REFERENCES contract(id),
    duty_type           VARCHAR(30)   NOT NULL CHECK (duty_type IN ('TRIP', 'DUTY')),
    completed_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    is_on_time          BOOLEAN       NOT NULL DEFAULT FALSE,
    status              VARCHAR(30)   NOT NULL DEFAULT 'COMPLETED'
        CHECK (status IN ('COMPLETED', 'CANCELLED', 'DISPUTED')),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (vendor_id, external_ref)
);

CREATE INDEX idx_trip_driver_time ON trip(driver_id, completed_at);
CREATE INDEX idx_trip_vendor_scope ON trip(vendor_id, business_unit_id, completed_at);

CREATE TABLE trip_violation (
    id                  BIGSERIAL PRIMARY KEY,
    trip_id             BIGINT        NOT NULL REFERENCES trip(id),
    violation_type      VARCHAR(50)   NOT NULL CHECK (violation_type IN ('OVER_SPEED', 'ROUTE_DEVIATION', 'NIGHT_SAFETY_BREACH')),
    event_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    source_ref          VARCHAR(200),
    status              VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DISMISSED')),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (trip_id, violation_type, source_ref)
);

CREATE INDEX idx_violation_trip ON trip_violation(trip_id);

-- 7. SETTLEMENT RUN (Two-Tier Idempotency & Partial Unique Scope Index)
CREATE TABLE settlement_run (
    id                  BIGSERIAL PRIMARY KEY,
    vendor_id           BIGINT        NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT        REFERENCES business_unit(id),
    period_start        DATE          NOT NULL,
    period_end          DATE          NOT NULL,
    run_type            VARCHAR(20)   NOT NULL DEFAULT 'ORIGINAL'
        CHECK (run_type IN ('ORIGINAL', 'ADJUSTMENT')),
    status              VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'CALCULATED', 'UNDER_REVIEW', 'APPROVED', 'FINALIZED', 'FAILED')),
    idempotency_key     VARCHAR(200)  NOT NULL,
    total_gross         NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_incentives    NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_penalties     NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_tds           NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_net           NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    trip_count          INTEGER       NOT NULL DEFAULT 0,
    driver_count        INTEGER       NOT NULL DEFAULT 0,
    calculation_version VARCHAR(50),
    config_hash         VARCHAR(64),
    initiated_by        VARCHAR(100),
    approved_by         VARCHAR(100),
    finalized_by        VARCHAR(100),
    error_message       TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_settlement_run_original_scope
    ON settlement_run(vendor_id, business_unit_id, period_start, period_end)
    WHERE run_type = 'ORIGINAL' AND status != 'FAILED';

CREATE INDEX idx_settlement_run_status ON settlement_run(status);

-- 8. DRIVER PAYSLIP (Aggregated Driver Summary)
CREATE TABLE driver_payslip (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT        NOT NULL REFERENCES settlement_run(id),
    driver_id           BIGINT        NOT NULL REFERENCES driver(id),
    gross_pay           NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_incentives    NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_penalties     NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_tds           NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    net_pay             NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    trip_count          INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, driver_id)
);

CREATE INDEX idx_payslip_driver ON driver_payslip(driver_id);

-- 9. SETTLEMENT LINE (Granular Signed Line Items)
CREATE TABLE settlement_line (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT        NOT NULL REFERENCES settlement_run(id),
    driver_id           BIGINT        NOT NULL REFERENCES driver(id),
    line_type           VARCHAR(30)   NOT NULL CHECK (line_type IN ('BASE_PAY', 'INCENTIVE', 'PENALTY', 'TDS')),
    source_type         VARCHAR(30),
    source_id           BIGINT,
    description         VARCHAR(500)  NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL,
    rate_version        INTEGER,
    policy_version      INTEGER,
    calculation_details JSONB,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, driver_id, line_type, source_type, source_id)
);

CREATE INDEX idx_line_run_driver ON settlement_line(run_id, driver_id);

-- 10. VENDOR INVOICE & RECONCILIATION
CREATE TABLE vendor_invoice (
    id                  BIGSERIAL PRIMARY KEY,
    vendor_id           BIGINT          NOT NULL REFERENCES vendor(id),
    business_unit_id    BIGINT          NOT NULL REFERENCES business_unit(id),
    invoice_ref         VARCHAR(100)    NOT NULL,
    period_start        DATE            NOT NULL,
    period_end          DATE            NOT NULL,
    amount              NUMERIC(15, 2)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'MATCHED', 'DISCREPANCY', 'RESOLVED')),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (vendor_id, invoice_ref)
);

CREATE TABLE reconciliation (
    id                      BIGSERIAL PRIMARY KEY,
    invoice_id              BIGINT          NOT NULL REFERENCES vendor_invoice(id),
    settlement_run_id       BIGINT          REFERENCES settlement_run(id),
    calculated_amount       NUMERIC(15, 2)  NOT NULL,
    invoice_amount          NUMERIC(15, 2)  NOT NULL,
    difference              NUMERIC(15, 2)  NOT NULL,
    tolerance               NUMERIC(15, 2)  NOT NULL,
    status                  VARCHAR(30)     NOT NULL CHECK (status IN ('MATCHED', 'DISCREPANCY', 'UNDER_REVIEW', 'RESOLVED')),
    accounting_basis        VARCHAR(50)     NOT NULL DEFAULT 'NET_DRIVER_PAYOUT',
    notes                   TEXT,
    resolved_by             VARCHAR(100),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_invoice ON reconciliation(invoice_id);

-- 11. DISPUTES & DELTA ADJUSTMENTS
CREATE TABLE dispute (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT          NOT NULL REFERENCES trip(id),
    settlement_run_id BIGINT        REFERENCES settlement_run(id),
    raised_by       VARCHAR(100)    NOT NULL,
    reason          TEXT            NOT NULL,
    evidence_ref    VARCHAR(500),
    status          VARCHAR(30)     NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'RESOLVED')),
    resolution      TEXT,
    approved_by     VARCHAR(100),
    resolved_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dispute_trip ON dispute(trip_id);
CREATE INDEX idx_dispute_run ON dispute(settlement_run_id);

CREATE TABLE settlement_adjustment (
    id                  BIGSERIAL PRIMARY KEY,
    dispute_id          BIGINT          NOT NULL REFERENCES dispute(id),
    original_run_id     BIGINT          NOT NULL REFERENCES settlement_run(id),
    adjustment_run_id   BIGINT          REFERENCES settlement_run(id),
    driver_id           BIGINT          NOT NULL REFERENCES driver(id),
    line_type           VARCHAR(30)     NOT NULL,
    original_amount     NUMERIC(15, 2)  NOT NULL,
    revised_amount      NUMERIC(15, 2)  NOT NULL,
    delta_amount        NUMERIC(15, 2)  NOT NULL,
    reason              TEXT            NOT NULL,
    version             INTEGER         NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (dispute_id, version)
);

-- 12. IDEMPOTENCY RECORD STORE (HTTP Tier)
CREATE TABLE idempotency_record (
    id                  BIGSERIAL PRIMARY KEY,
    idempotency_key     VARCHAR(200) NOT NULL UNIQUE,
    resource_path       VARCHAR(200) NOT NULL,
    request_hash        VARCHAR(64)  NOT NULL,
    status              VARCHAR(30)  NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    response_status     INTEGER,
    response_payload    TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_idempotency_key ON idempotency_record(idempotency_key);

-- 13. PAYOUT INSTRUCTION & TRANSACTIONAL OUTBOX
CREATE TABLE payout_instruction (
    id                  BIGSERIAL PRIMARY KEY,
    settlement_run_id   BIGINT          NOT NULL REFERENCES settlement_run(id),
    driver_id           BIGINT          REFERENCES driver(id),
    vendor_id           BIGINT          NOT NULL REFERENCES vendor(id),
    amount              NUMERIC(15, 2)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    idempotency_key     VARCHAR(200)    NOT NULL UNIQUE,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'INITIATED', 'SUCCESS', 'FAILED', 'CANCELLED')),
    provider_reference  VARCHAR(200),
    failure_reason      TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payout_run ON payout_instruction(settlement_run_id);
CREATE INDEX idx_payout_driver ON payout_instruction(driver_id);
CREATE INDEX idx_payout_status ON payout_instruction(status);

CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    BIGINT          NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status ON outbox_event(status);

-- 14. AUDIT EVENT (Append-Only)
CREATE TABLE audit_event (
    id              BIGSERIAL PRIMARY KEY,
    actor           VARCHAR(100)    NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       BIGINT,
    correlation_id  VARCHAR(100),
    details         JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);
CREATE INDEX idx_audit_correlation ON audit_event(correlation_id);
