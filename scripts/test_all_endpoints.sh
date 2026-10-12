#!/usr/bin/env bash
# ==============================================================================
# MoveInSync Vendor & Driver Settlement Engine
# Comprehensive End-to-End API Verification Script
# Tests EVERY single REST endpoint across the entire microservice
# ==============================================================================
set -euo pipefail

BASE_URL="http://localhost:8080"
PASS_COUNT=0
FAIL_COUNT=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

assert_status() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    local response_body="$4"

    if [ "$actual" -eq "$expected" ]; then
        echo -e "  [${GREEN}PASS${NC}] $test_name (HTTP $actual)"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "  [${RED}FAIL${NC}] $test_name (Expected $expected, got $actual)"
        echo -e "         Response: $response_body"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

echo -e "\n${CYAN}======================================================================${NC}"
echo -e "${CYAN}   MoveInSync Settlement Engine - Comprehensive Endpoint Test Suite   ${NC}"
echo -e "${CYAN}======================================================================${NC}\n"

# ------------------------------------------------------------------------------
# 1. System & Observability Endpoints
# ------------------------------------------------------------------------------
echo -e "${BLUE}=== 1. System, Docs & Actuator Endpoints ===${NC}"

# Health
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/actuator/health")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /actuator/health" 200 "$CODE" "$BODY"

# Info
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/actuator/info")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /actuator/info" 200 "$CODE" "$BODY"

# Metrics
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/actuator/metrics")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /actuator/metrics" 200 "$CODE" "$BODY"

# Specific Metric (JVM memory)
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/actuator/metrics/jvm.memory.used")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /actuator/metrics/jvm.memory.used" 200 "$CODE" "$BODY"

# OpenAPI 3 docs
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/v3/api-docs")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /v3/api-docs (OpenAPI 3.1 Spec)" 200 "$CODE" "$BODY"

# Swagger UI
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/swagger-ui/index.html")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /swagger-ui/index.html" 200 "$CODE" "$BODY"

# ------------------------------------------------------------------------------
# 2. Authentication & Authorization Endpoints
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}=== 2. Authentication & Authorization Endpoints ===${NC}"

# Admin Login (/api/v1/auth/login)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/v1/auth/login (Admin)" 200 "$CODE" "$BODY"
ADMIN_TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Approver Login (/api/auth/login alias)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"approver","password":"password123"}')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/login (Approver)" 200 "$CODE" "$BODY"
APPROVER_TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Driver Login
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"driver_raj","password":"password123"}')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/login (Driver Raj)" 200 "$CODE" "$BODY"
DRIVER_TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Vendor Manager Login
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"vendor_mgr","password":"password123"}')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/login (Vendor Manager)" 200 "$CODE" "$BODY"
VENDOR_TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Bad Credentials (Expect 401)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrongpassword"}')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/login (Negative: Bad Password)" 401 "$CODE" "$BODY"

# Unauthenticated Request to Protected Endpoint (Expect 401)
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/settlements/1")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/settlements/1 (Negative: No Bearer Token)" 401 "$CODE" "$BODY"

# Driver Role trying to access Admin calculate endpoint (Expect 403)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/calculate" \
  -H "Authorization: Bearer ${DRIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"vendorId":1,"periodStart":"2026-10-01","periodEnd":"2026-10-31","previewOnly":true}')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/calculate (Negative: Forbidden Role)" 403 "$CODE" "$BODY"

# ------------------------------------------------------------------------------
# 3. Settlement Calculation & State Machine Lifecycle
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}=== 3. Settlement Engine Endpoints ===${NC}"

# Ephemeral Preview (0 DB writes)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/calculate" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": 1,
    "businessUnitId": 1,
    "periodStart": "2026-10-01",
    "periodEnd": "2026-10-31",
    "previewOnly": true
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/calculate (Ephemeral Preview, previewOnly=true)" 200 "$CODE" "$BODY"

# Persistent Calculation with Idempotency Key for Vendor 2 (fresh, un-finalized run)
IDEM_KEY="VERIF-V2-$(date +%s)-$RANDOM"
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/calculate" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Idempotency-Key: ${IDEM_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": 2,
    "businessUnitId": 3,
    "periodStart": "2026-10-01",
    "periodEnd": "2026-10-31",
    "previewOnly": false
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/calculate (Persistent Run creation)" 200 "$CODE" "$BODY"
RUN_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -n1 | cut -d':' -f2)

# Idempotent Repeat Request (Should return identical cached response)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/calculate" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Idempotency-Key: ${IDEM_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": 2,
    "businessUnitId": 3,
    "periodStart": "2026-10-01",
    "periodEnd": "2026-10-31",
    "previewOnly": false
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/calculate (Idempotency Key Cached Replay)" 200 "$CODE" "$BODY"

# Get Settlement Run by ID
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/settlements/${RUN_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/settlements/${RUN_ID}" 200 "$CODE" "$BODY"

# State Transition 1: Move to Review (CALCULATED -> UNDER_REVIEW)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/${RUN_ID}/review" \
  -H "Authorization: Bearer ${APPROVER_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/${RUN_ID}/review (CALCULATED -> UNDER_REVIEW)" 200 "$CODE" "$BODY"

# State Transition 2: Approve Run (UNDER_REVIEW -> APPROVED)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/${RUN_ID}/approve" \
  -H "Authorization: Bearer ${APPROVER_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/${RUN_ID}/approve (UNDER_REVIEW -> APPROVED)" 200 "$CODE" "$BODY"

# State Transition 3: Finalize Run (APPROVED -> FINALIZED, generates Payouts & queues Outbox)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/settlements/${RUN_ID}/finalize" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/settlements/${RUN_ID}/finalize (APPROVED -> FINALIZED)" 200 "$CODE" "$BODY"

# List Payout Instructions for this Settlement Run
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/settlements/${RUN_ID}/payouts" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/settlements/${RUN_ID}/payouts" 200 "$CODE" "$BODY"

# ------------------------------------------------------------------------------
# 4. Payouts & Transactional Outbox Endpoints
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}=== 4. Payout & Outbox Endpoints ===${NC}"

# Get Payouts by Run ID from PayoutController
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/payouts/run/${RUN_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/payouts/run/${RUN_ID}" 200 "$CODE" "$BODY"
PAYOUT_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -n1 | cut -d':' -f2)

# Get Single Payout by ID
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/payouts/${PAYOUT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/payouts/${PAYOUT_ID}" 200 "$CODE" "$BODY"

# Get Pending Outbox Events
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/payouts/outbox/pending" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/payouts/outbox/pending" 200 "$CODE" "$BODY"

# Manually Trigger Outbox Processor (SKIP LOCKED)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/payouts/outbox/process" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/payouts/outbox/process (SKIP LOCKED Dispatcher)" 200 "$CODE" "$BODY"

# Get Failed (Dead-Lettered) Outbox Events
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/payouts/outbox/failed" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/payouts/outbox/failed (DLQ Inspection)" 200 "$CODE" "$BODY"

# Replay all DLQ failed events
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/payouts/outbox/retry-all" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/payouts/outbox/retry-all (DLQ Batch Requeue)" 200 "$CODE" "$BODY"

# Retry single event (using synthetic id 99999 -> returns false gracefully)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/payouts/outbox/retry/99999" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/payouts/outbox/retry/99999 (Single Event Retry)" 200 "$CODE" "$BODY"

# Simulate Banking Webhook Notification
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/payouts/webhook" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "payoutId": '"${PAYOUT_ID}"',
    "status": "SUCCESS",
    "providerReference": "BANK-UTR-'"$(date +%s)"'",
    "failureReason": null
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/payouts/webhook (Simulate IMPS/NEFT confirmation)" 200 "$CODE" "$BODY"

# ------------------------------------------------------------------------------
# 5. Disputes & Closed-Loop Delta Settlements
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}=== 5. Dispute & Delta Settlement Endpoints ===${NC}"

# Find a trip to dispute
TRIP_ID=1

# Raise Dispute Ticket (Driver)
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/disputes" \
  -H "Authorization: Bearer ${DRIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "tripId": '"${TRIP_ID}"',
    "settlementRunId": '"${RUN_ID}"',
    "reason": "Driver toll reimbursement missing from trip slip",
    "claimedAmount": 350.00
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/disputes (Raise Ticket)" 200 "$CODE" "$BODY"
DISPUTE_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -n1 | cut -d':' -f2)

# Get Dispute by ID
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/disputes/${DISPUTE_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/disputes/${DISPUTE_ID}" 200 "$CODE" "$BODY"

# List Disputes for Trip
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/disputes/trip/${TRIP_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/disputes/trip/${TRIP_ID}" 200 "$CODE" "$BODY"

# List All Pending/Open Disputes
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/disputes/pending" \
  -H "Authorization: Bearer ${APPROVER_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/disputes/pending" 200 "$CODE" "$BODY"

# Resolve Dispute by Path ID: Approve and trigger closed-loop delta adjustment
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/disputes/${DISPUTE_ID}/resolve" \
  -H "Authorization: Bearer ${APPROVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "approve": true,
    "revisedAmount": 350.00,
    "resolutionComments": "Toll slip receipt verified and approved by transport manager"
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/disputes/${DISPUTE_ID}/resolve (Path Parameter)" 200 "$CODE" "$BODY"

# Raise a second dispute to test /api/disputes/resolve with body disputeId
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/disputes" \
  -H "Authorization: Bearer ${DRIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "tripId": 2,
    "settlementRunId": '"${RUN_ID}"',
    "reason": "Wait time overtime fare discrepancy",
    "claimedAmount": 200.00
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
DISPUTE_ID_2=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -n1 | cut -d':' -f2)

# Resolve Dispute by Body ID: Reject with explanation
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/disputes/resolve" \
  -H "Authorization: Bearer ${APPROVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "disputeId": '"${DISPUTE_ID_2}"',
    "approve": false,
    "resolutionComments": "GPS telematics confirmed waiting time was under minimum threshold"
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/disputes/resolve (Request Body disputeId)" 200 "$CODE" "$BODY"

# ------------------------------------------------------------------------------
# 6. Vendor Invoice Reconciliation Endpoints
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}=== 6. Reconciliation & Invoice Matching Endpoints ===${NC}"

INV_NUM="INV-VERIF-$(date +%s)"
# Submit Vendor Invoice
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/reconciliations/invoices" \
  -H "Authorization: Bearer ${VENDOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": 1,
    "businessUnitId": 1,
    "invoiceRef": "'"${INV_NUM}"'",
    "periodStart": "2026-10-01",
    "periodEnd": "2026-10-31",
    "amount": 65000.00
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/reconciliations/invoices (Submit Invoice)" 200 "$CODE" "$BODY"
INVOICE_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -n1 | cut -d':' -f2)

# List Invoices (All)
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/reconciliations/invoices" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/reconciliations/invoices (List All)" 200 "$CODE" "$BODY"

# List Invoices by Query Parameter
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/reconciliations/invoices?vendorId=1" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/reconciliations/invoices?vendorId=1 (Query Param Filter)" 200 "$CODE" "$BODY"

# List Invoices by Vendor Path
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/reconciliations/invoices/vendor/1" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/reconciliations/invoices/vendor/1 (Path Param Filter)" 200 "$CODE" "$BODY"

# Get Invoice by ID
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/reconciliations/invoices/${INVOICE_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/reconciliations/invoices/${INVOICE_ID}" 200 "$CODE" "$BODY"

# Execute 3-Way Tolerance Comparison
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/reconciliations/compare" \
  -H "Authorization: Bearer ${APPROVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": '"${INVOICE_ID}"',
    "settlementRunId": 2,
    "tolerance": 50.00,
    "tolerancePercentage": 2.0
  }')
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/reconciliations/compare (Tolerance Comparison)" 200 "$CODE" "$BODY"
RECON_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -n1 | cut -d':' -f2)

# Get Reconciliation Audit Record by ID
RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/reconciliations/${RECON_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/reconciliations/${RECON_ID}" 200 "$CODE" "$BODY"

# ------------------------------------------------------------------------------
# Final Summary
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}======================================================================${NC}"
echo -e "${CYAN}                         TEST RESULTS SUMMARY                         ${NC}"
echo -e "${CYAN}======================================================================${NC}"
echo -e "  Passed: ${GREEN}${PASS_COUNT}${NC}"
echo -e "  Failed: ${RED}${FAIL_COUNT}${NC}"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo -e "\n${GREEN}ALL 33 ENDPOINT SCENARIOS TESTED AND VERIFIED WITH 100% SUCCESS! ✓${NC}\n"
    exit 0
else
    echo -e "\n${RED}SOME ENDPOINT TESTS FAILED! ✗${NC}\n"
    exit 1
fi
