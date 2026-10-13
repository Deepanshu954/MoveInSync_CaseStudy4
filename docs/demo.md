# 📸 MoveInSync Case Study 4 — Live API Demonstration

This document contains live Postman execution screenshots demonstrating all core functional workflows of the **Vendor & Driver Settlement Engine**.

---

## 📑 Demonstration Workflow Index

1. [Authentication & JWT Generation](#1-authentication--jwt-token-generation)
2. [Calculate Monthly Settlement Run (Part 1 & 2)](#2-calculate-monthly-settlement-run)
3. [View Settlement Run & Itemized Driver Payslips](#3-view-settlement-run--driver-payslips)
4. [3-Way Reconciliation Tolerance Matching](#4-3-way-reconciliation-tolerance-matching)
5. [Dispute Resolution & Delta Payout Generation](#5-dispute-resolution--delta-payout-generation)
6. [Banking Payout Instructions & Bank UTR Numbers](#6-banking-payout-instructions--bank-utr-numbers)

---

## 1. Authentication & JWT Token Generation
* **Endpoint:** `POST /api/v1/auth/login`
* **Description:** Authenticates the settlement admin user and issues a signed, stateless HMAC-SHA512 JWT Bearer token with RBAC role `ROLE_SETTLEMENT_ADMIN`.

![Authentication & JWT Generation](screenshots/Authentication%20&%20JWT%20Generation.png)

---

## 2. Calculate Monthly Settlement Run
* **Endpoint:** `POST /api/settlements/calculate`
* **Description:** Executes batch calculation for ACME Transport (`vendorId: 1`) across October 2026. Evaluates 230 trips, applies mid-month rate card version 2, calculates on-time incentives, deducts capped SLA penalties, and computes statutory Section 194C TDS withholding with zero mathematical drift.

### Calculation Request & Aggregated Totals
![Calculate Monthly Settlement Run - Part 1](screenshots/Calculate%20Monthly%20Settlement%20Run-1.png)

### Driver Line Items & Incentive/Penalty Breakdown
![Calculate Monthly Settlement Run - Part 2](screenshots/Calculate%20Monthly%20Settlement%20Run-2.png)

---

## 3. View Settlement Run & Driver Payslips
* **Endpoint:** `GET /api/settlements/2`
* **Description:** Retrieves the finalized settlement statement showing run totals (`totalGross: ₹60,225.00`, `totalNet: ₹64,775.00`) and individual digital payslips for Raj Kumar (₹48,050.00) and Priya Sharma (₹16,725.00).

![View Settlement Run & Itemized Driver Payslips](screenshots/View%20Settlement%20Run%20&%20Itemized%20Driver%20Payslips.png)

---

## 4. 3-Way Reconciliation Tolerance Matching
* **Endpoint:** `POST /api/reconciliations/compare`
* **Description:** Executes 3-way automated matching comparing Vendor Invoice (`INV-LIVE-VAL-1791716660`, ₹64,800.00) against Platform Net Calculation (`Run #2`, ₹64,775.00). Validates that the ₹25.00 variance is within the allowed ₹50.00 tolerance window and stamps status as `MATCHED`.

![3-Way Reconciliation Comparison](screenshots/3-Way%20Reconciliation%20Comparison.png)

---

## 5. Dispute Resolution & Delta Payout Generation
* **Endpoint:** `POST /api/disputes/31/resolve`
* **Description:** Resolves an open dispute ticket filed for unbilled rain storm detour miles. Approves a ₹100.00 delta credit adjustment and automatically generates a pending payout instruction for immediate disbursement without mutating past closed billing records.

![Dispute Resolution & Delta Payout Generation](screenshots/Dispute%20Resolution%20&%20Delta%20Payout%20Generation.png)

---

## 6. Banking Payout Instructions & Bank UTR Numbers
* **Endpoint:** `GET /api/settlements/2/payouts`
* **Description:** Displays the banking disbursement ledger for the settlement run, showing successful payments, unique transaction references (Bank UTR numbers e.g. `UTR-20261011-23C6E7CA`), and payout timestamps.

![Banking Payout Instructions & Bank UTR Numbers](screenshots/Banking%20Payout%20Instructions%20&%20Bank%20UTR%20Numbers.png)
