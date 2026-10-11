-- ============================================================
-- V2__seed_data.sql
-- Seed dataset for Case Study 4: ACME Vendor, Drivers, Rates & Trips
-- All demo user passwords: 'password123'
-- ============================================================

-- 1. USERS
INSERT INTO app_user (username, password_hash, full_name, email, role) VALUES
    ('admin',       '$2a$10$9XzH2uNAq7GKfUrj7mWstelsfBvEj1MoBbtK05iHqv0zdkhLGFezG', 'System Admin',     'admin@moveinsync.com',     'SETTLEMENT_ADMIN'),
    ('approver',    '$2a$10$9XzH2uNAq7GKfUrj7mWstelsfBvEj1MoBbtK05iHqv0zdkhLGFezG', 'Finance Approver',  'approver@moveinsync.com',  'APPROVER'),
    ('vendor_mgr',  '$2a$10$9XzH2uNAq7GKfUrj7mWstelsfBvEj1MoBbtK05iHqv0zdkhLGFezG', 'Vendor Manager',    'vendor@acme.com',          'VENDOR_ADMIN'),
    ('driver_raj',  '$2a$10$9XzH2uNAq7GKfUrj7mWstelsfBvEj1MoBbtK05iHqv0zdkhLGFezG', 'Raj Kumar',         'raj@example.com',          'DRIVER'),
    ('driver_priya','$2a$10$9XzH2uNAq7GKfUrj7mWstelsfBvEj1MoBbtK05iHqv0zdkhLGFezG', 'Priya Sharma',      'priya@example.com',        'DRIVER');

-- 2. VENDORS & BUSINESS UNITS
INSERT INTO vendor (id, code, name, status) VALUES
    (1, 'ACME', 'ACME Transport Services', 'ACTIVE'),
    (2, 'BETA', 'Beta Logistics Pvt Ltd',  'ACTIVE');

INSERT INTO business_unit (id, vendor_id, code, name) VALUES
    (1, 1, 'ACME-BLR', 'ACME Bangalore Operations'),
    (2, 1, 'ACME-HYD', 'ACME Hyderabad Operations'),
    (3, 2, 'BETA-PUN', 'Beta Pune Operations');

-- 3. DRIVERS
INSERT INTO driver (id, vendor_id, business_unit_id, external_ref, name, status, app_user_id) VALUES
    (1, 1, 1, 'DRV-RAJ-001',   'Raj Kumar',     'ACTIVE', 4),
    (2, 1, 1, 'DRV-PRIYA-002', 'Priya Sharma',  'ACTIVE', 5),
    (3, 1, 2, 'DRV-AMIT-003',  'Amit Verma',    'ACTIVE', NULL),
    (4, 2, 3, 'DRV-SURESH-004','Suresh Patil',  'ACTIVE', NULL);

-- 4. CONTRACTS & VERSIONED RATES (Mid-Month Rate Change on Oct 15)
INSERT INTO contract (id, vendor_id, business_unit_id, name, currency, status) VALUES
    (1, 1, 1, 'ACME Bangalore Master Agreement 2026', 'INR', 'ACTIVE'),
    (2, 2, 3, 'Beta Pune Logistics Agreement 2026',  'INR', 'ACTIVE');

INSERT INTO contract_rate (contract_id, component_type, amount, effective_from, effective_to, version) VALUES
    (1, 'PER_TRIP', 250.00, '2026-01-01T00:00:00+05:30', '2026-10-15T00:00:00+05:30', 1),
    (1, 'PER_TRIP', 275.00, '2026-10-15T00:00:00+05:30', NULL,                        2),
    (1, 'PER_DUTY', 500.00, '2026-01-01T00:00:00+05:30', NULL,                        1),
    (2, 'PER_TRIP', 240.00, '2026-01-01T00:00:00+05:30', NULL,                        1);

-- 5. INCENTIVE POLICIES
INSERT INTO incentive_policy (vendor_id, business_unit_id, type, amount, threshold_value, threshold_percent, effective_from, effective_to, version) VALUES
    (1, 1, 'ON_TIME_BONUS',      2000.00, NULL, 90.00, '2026-01-01T00:00:00+05:30', NULL, 1),
    (1, 1, 'WORKED_DAYS_BONUS',  1500.00, 20,   NULL,  '2026-01-01T00:00:00+05:30', NULL, 1),
    (1, 1, 'ATTENDANCE_BONUS',   1000.00, 26,   NULL,  '2026-01-01T00:00:00+05:30', NULL, 1);

-- 6. PENALTY POLICIES
INSERT INTO penalty_policy (vendor_id, business_unit_id, violation_type, amount, frequency_window, max_occurrences, effective_from, effective_to, version) VALUES
    (1, 1, 'OVER_SPEED',          500.00, 'DAILY',   2,    '2026-01-01T00:00:00+05:30', NULL, 1),
    (1, 1, 'ROUTE_DEVIATION',     300.00, 'WEEKLY',  3,    '2026-01-01T00:00:00+05:30', NULL, 1),
    (1, 1, 'NIGHT_SAFETY_BREACH', 1000.00,'MONTHLY', NULL, '2026-01-01T00:00:00+05:30', NULL, 1);

-- 7. TAX CONFIGURATION (TDS: 10% on excess above ₹30,000/month)
INSERT INTO tax_config (name, monthly_threshold, rate_percent, effective_from, effective_to, version) VALUES
    ('Standard TDS (10% over 30k)', 30000.00, 0.1000, '2026-01-01T00:00:00+05:30', NULL, 1);

-- 8. SAMPLE TRIPS FOR DRIVER 1 (Raj Kumar) - 180 TRIPS TOTAL
-- 100 trips before Oct 15 @ ₹250 (first 90 on-time)
INSERT INTO trip (external_ref, driver_id, vendor_id, business_unit_id, contract_id, duty_type, completed_at, is_on_time, status)
SELECT
    'TRIP-RAJ-OCT-' || LPAD(s::text, 4, '0'),
    1, 1, 1, 1,
    'TRIP',
    ('2026-10-01 08:00:00+05:30'::timestamptz + ((s - 1) * INTERVAL '3 hours 12 minutes')),
    (s <= 90),
    'COMPLETED'
FROM generate_series(1, 100) AS s;

-- 80 trips on/after Oct 15 @ ₹275 (first 72 on-time)
INSERT INTO trip (external_ref, driver_id, vendor_id, business_unit_id, contract_id, duty_type, completed_at, is_on_time, status)
SELECT
    'TRIP-RAJ-OCT-' || LPAD((s + 100)::text, 4, '0'),
    1, 1, 1, 1,
    'TRIP',
    ('2026-10-15 08:00:00+05:30'::timestamptz + ((s - 1) * INTERVAL '4 hours 48 minutes')),
    (s <= 72),
    'COMPLETED'
FROM generate_series(1, 80) AS s;

-- 9. SAMPLE TRIPS FOR DRIVER 2 (Priya Sharma) - 50 TRIPS TOTAL
INSERT INTO trip (external_ref, driver_id, vendor_id, business_unit_id, contract_id, duty_type, completed_at, is_on_time, status)
SELECT
    'TRIP-PRIYA-OCT-' || LPAD(s::text, 4, '0'),
    2, 1, 1, 1,
    'TRIP',
    ('2026-10-05 09:00:00+05:30'::timestamptz + ((s - 1) * INTERVAL '11 hours')),
    (s <= 45),
    'COMPLETED'
FROM generate_series(1, 50) AS s;

-- 10. SAMPLE VIOLATIONS FOR DRIVER 1
INSERT INTO trip_violation (trip_id, violation_type, event_at, source_ref, status) VALUES
    (5,  'OVER_SPEED',      '2026-10-02 10:15:00+05:30', 'GPS-SPEED-001', 'ACTIVE'),
    (12, 'OVER_SPEED',      '2026-10-03 14:22:00+05:30', 'GPS-SPEED-002', 'ACTIVE'),
    (45, 'ROUTE_DEVIATION', '2026-10-08 19:40:00+05:30', 'GPS-DEV-001',   'ACTIVE'),
    (60, 'OVER_SPEED',      '2026-10-10 11:00:00+05:30', 'GPS-SPEED-003', 'DISMISSED');

-- Reset sequences to align with inserted IDs
SELECT setval('app_user_id_seq', (SELECT MAX(id) FROM app_user));
SELECT setval('vendor_id_seq', (SELECT MAX(id) FROM vendor));
SELECT setval('business_unit_id_seq', (SELECT MAX(id) FROM business_unit));
SELECT setval('driver_id_seq', (SELECT MAX(id) FROM driver));
SELECT setval('contract_id_seq', (SELECT MAX(id) FROM contract));
SELECT setval('contract_rate_id_seq', (SELECT MAX(id) FROM contract_rate));
SELECT setval('incentive_policy_id_seq', (SELECT MAX(id) FROM incentive_policy));
SELECT setval('penalty_policy_id_seq', (SELECT MAX(id) FROM penalty_policy));
SELECT setval('tax_config_id_seq', (SELECT MAX(id) FROM tax_config));
SELECT setval('trip_id_seq', (SELECT MAX(id) FROM trip));
SELECT setval('trip_violation_id_seq', (SELECT MAX(id) FROM trip_violation));
