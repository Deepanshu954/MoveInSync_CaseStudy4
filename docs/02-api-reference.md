# API Reference
## MoveInSync — Vendor & Driver Settlement Engine

Base URL: `http://localhost:8080`

All endpoints (except `/api/v1/auth/login`) require:
```
Authorization: Bearer <jwt-token>
```

---

## Authentication

### Login
`POST /api/v1/auth/login`

Authenticate and receive a 24-hour JWT token.

**Request Body:**
```json
{
  "username": "admin",
  "password": "password123"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000,
  "role": "SETTLEMENT_ADMIN"
}
```

**Seed accounts (from V2 migration — password for all: `password123`):**

| Username | Role |
|---|---|
| `admin` | `SETTLEMENT_ADMIN` |
| `approver` | `APPROVER` |
| `vendor_mgr` | `VENDOR_ADMIN` |
| `driver_raj` | `DRIVER` |
| `driver_priya` | `DRIVER` |

---

## Settlement

### Calculate or Preview Settlement
`POST /api/settlements/calculate`

Runs the financial engine for a vendor/period. Pass `previewOnly: true` to get the full calculation result without writing anything to the database (useful for finance review before committing).

**Headers:**
- `Idempotency-Key: <string>` — optional; makes the call run-once-safe

**Request Body:**
```json
{
  "vendorId": 1,
  "businessUnitId": 1,
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31",
  "previewOnly": false
}
```

**Response `200 OK`:**
```json
{
  "id": 42,
  "vendorId": 1,
  "businessUnitId": 1,
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31",
  "status": "CALCULATED",
  "runType": "ORIGINAL",
  "totalGross": "65000.00",
  "totalIncentives": "3500.00",
  "totalPenalties": "1000.00",
  "totalTds": "375.00",
  "totalNet": "67125.00",
  "tripCount": 230,
  "driverCount": 3,
  "payslips": [
    {
      "driverId": 1,
      "driverName": "Raj Kumar",
      "grossPay": "44500.00",
      "incentives": "2000.00",
      "penalties": "500.00",
      "tdsDeducted": "160.00",
      "netPay": "45840.00"
    }
  ]
}
```

---

### Get Settlement Run
`GET /api/settlements/{id}`

Returns the full settlement run including all driver payslips.

---

### Move to Review
`POST /api/settlements/{id}/review`

Transitions a `CALCULATED` run to `UNDER_REVIEW`. Call this when the finance team wants to flag the run for formal review before approval.

Requires role: `SETTLEMENT_ADMIN` or `APPROVER`

---

### Approve Settlement
`POST /api/settlements/{id}/approve`

Approves a `CALCULATED` or `UNDER_REVIEW` run. Uses `SELECT FOR UPDATE` to prevent concurrent approvals.

Requires role: `APPROVER`

---

### Finalize Settlement (Generate Payouts)
`POST /api/settlements/{id}/finalize`

Finalizes an `APPROVED` run. This is an **irreversible** action that:
1. Validates the financial invariant: `|run.totalNet - Σ payslips| ≤ ₹0.01`
2. Generates one `PayoutInstruction` per driver
3. Writes `SETTLEMENT_FINALIZED` and `PAYOUT_INITIATED` events to the Transactional Outbox
4. All three steps are in a single database transaction

Requires role: `APPROVER`

**Response `200 OK`:**
```json
{
  "runId": 42,
  "status": "FINALIZED",
  "instructionsCreated": 3,
  "totalDisbursed": "67125.00"
}
```

---

### List Payouts for a Run
`GET /api/settlements/{id}/payouts`

Returns all `PayoutInstruction` records for a finalized settlement run.

---

## Payouts & Outbox

### Get Payout Instruction
`GET /api/payouts/{id}`

---

### List Payouts for a Run
`GET /api/payouts/run/{runId}`

---

### Trigger Outbox Processing
`POST /api/payouts/outbox/process`

Manually triggers the `OutboxProcessorTask` to dispatch pending banking events. Normally this happens automatically every 5 seconds.

**Response `200 OK`:**
```json
{
  "totalEvaluated": 3,
  "processedCount": 3,
  "failedCount": 0,
  "pendingRemaining": 0
}
```

---

### View Pending Outbox Events
`GET /api/payouts/outbox/pending`

---

### View Failed (DLQ) Outbox Events
`GET /api/payouts/outbox/failed`

---

### Retry a Single Failed Event
`POST /api/payouts/outbox/retry/{eventId}`

Requeues a specific dead-lettered event and attempts immediate dispatch.

---

### Retry All Failed Events
`POST /api/payouts/outbox/retry-all`

---

### Banking Webhook
`POST /api/payouts/webhook`

Receives callbacks from the banking gateway (UTR confirmation, failure notice).

**Request Body:**
```json
{
  "payoutInstructionId": 15,
  "status": "SUCCESS",
  "utrReference": "UTR20261011123456",
  "remarks": "NEFT transfer confirmed"
}
```

---

## Reconciliation

### Submit Vendor Invoice
`POST /api/reconciliations/invoices`

Vendor submits their month-end invoice for comparison against the engine's calculated totals.

**Request Body:**
```json
{
  "vendorId": 1,
  "businessUnitId": 1,
  "invoiceRef": "INV-ACME-OCT-2026",
  "amount": 67000.00,
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31"
}
```

---

### List All Invoices
`GET /api/reconciliations/invoices`

---

### Get Invoice
`GET /api/reconciliations/invoices/{id}`

---

### Get Vendor's Invoices
`GET /api/reconciliations/invoices/vendor/{vendorId}`

---

### Compare Invoice vs. Settlement Run
`POST /api/reconciliations/compare`

Performs the three-way reconciliation: vendor invoice amount vs. engine-calculated net. Flags `MATCHED` if within tolerance, `DISCREPANCY` otherwise.

**Request Body:**
```json
{
  "invoiceId": 7,
  "settlementRunId": 42,
  "toleranceAmount": 50.00,
  "tolerancePercent": 0.5,
  "accountingBasis": "NET_DRIVER_PAYOUT"
}
```

**Response `200 OK`:**
```json
{
  "id": 9,
  "invoiceId": 7,
  "settlementRunId": 42,
  "invoiceAmount": "67000.00",
  "calculatedAmount": "67125.00",
  "delta": "-125.00",
  "status": "DISCREPANCY",
  "toleranceAmount": "50.00",
  "message": "Invoice amount ₹67,000.00 vs calculated ₹67,125.00 (delta ₹125.00 exceeds tolerance ₹50.00)"
}
```

---

### Get Reconciliation Record
`GET /api/reconciliations/{id}`

---

## Disputes

### Raise a Trip Dispute
`POST /api/disputes`

A driver or vendor raises a billing dispute for a specific trip in an already-finalized run.

**Request Body:**
```json
{
  "tripId": 5,
  "settlementRunId": 42,
  "reason": "Toll fare not included in the original rate card",
  "evidenceRef": "TOLL-RECEIPT-20261002"
}
```

---

### Get Dispute
`GET /api/disputes/{id}`

---

### Get Disputes for a Trip
`GET /api/disputes/trip/{tripId}`

---

### List Pending Disputes
`GET /api/disputes/pending`

---

### Resolve a Dispute
`POST /api/disputes/{id}/resolve`

Finance approves or rejects the dispute. If approved:
1. A `SettlementAdjustment` record is appended (original lines never modified)
2. A new `PayoutInstruction` is created for the **delta amount only**
3. A new `OutboxEvent` queues the delta disbursement

**Request Body:**
```json
{
  "approve": true,
  "revisedAmount": 350.00,
  "resolutionComments": "Toll adjustment of ₹100 approved"
}
```

**Response `200 OK`:**
```json
{
  "disputeId": 3,
  "status": "APPROVED",
  "originalAmount": "250.00",
  "revisedAmount": "350.00",
  "deltaAmount": "100.00",
  "payoutInstructionId": 67
}
```

---

## Error Responses

All errors follow RFC 7807 Problem Details format:

```json
{
  "timestamp": "2026-10-11T18:30:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Financial invariant violation: run net ₹67125.00 != sum of payslips ₹67124.99 (drift: ₹0.01)",
  "path": "/api/settlements/42/finalize",
  "traceId": "abc-123-def-456"
}
```

| HTTP Status | Condition |
|---|---|
| `400` | Malformed request body / invalid parameter type |
| `401` | Missing or expired JWT |
| `403` | Authenticated but insufficient role |
| `404` | Resource not found |
| `409` | Idempotency conflict / duplicate resource |
| `422` | Business rule violation (invalid state transition, financial invariant failure) |
| `500` | Unexpected server error |

---

## Observability Endpoints

```
GET  /actuator/health              — Application health (DB, disk, JVM)
GET  /actuator/info                — App metadata
GET  /actuator/metrics             — All registered Micrometer metrics
GET  /actuator/metrics/{name}      — Single metric (e.g. settlement.runs.calculated)
GET  /actuator/prometheus          — Prometheus scrape endpoint
```

No authentication required for actuator endpoints.
