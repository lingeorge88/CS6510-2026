# API Contract Reference: Self-Checkout Monolith

**Date**: 2026-09-12 | **Source**: `spec/self-checkout-openapi.yaml`

This is a developer-friendly summary of the API contract. The OpenAPI spec is the authoritative source.

## Base URL

`http://localhost:8080`

## Endpoints

### 1. GET /items

**Purpose**: Return full catalog. Client fetches once at startup.

**Response 200**:
```json
{
  "items": [
    { "sku": "SKU-000001", "name": "Item 1", "price": 0.85 },
    { "sku": "SKU-000002", "name": "Item 2", "price": 1.20 }
  ]
}
```

---

### 2. POST /transactions

**Purpose**: Start a new transaction at a checkout station.

**Request**:
```json
{ "stationId": "station-001" }
```

**Response 201**:
```json
{
  "transactionId": "tx-abc123",
  "stationId": "station-001",
  "status": "OPEN",
  "itemCount": 0,
  "runningTotal": 0.0,
  "startedAt": "2026-09-12T10:30:00Z"
}
```

**Error 400**: `{ "error": "INVALID_REQUEST", "message": "stationId is required" }`

---

### 3. POST /transactions/{transactionId}/items

**Purpose**: Scan one unit of an item into the basket.

**Request**:
```json
{ "sku": "SKU-000042" }
```

**Response 200**:
```json
{
  "transactionId": "tx-abc123",
  "sku": "SKU-000042",
  "name": "Item 42",
  "unitPrice": 7.85,
  "itemCount": 3,
  "runningTotal": 15.70
}
```

**Error 404** (unknown transaction): `{ "error": "NOT_FOUND", "message": "No such transaction" }`
**Error 404** (unknown SKU): `{ "error": "UNKNOWN_SKU", "message": "No such SKU" }`
**Error 409** (not open): `{ "error": "TRANSACTION_NOT_OPEN", "message": "Transaction is not open" }`

---

### 4. POST /transactions/{transactionId}/complete

**Purpose**: Pay, decrement stock for all scanned items, return receipt.

**Request**: `{}` (empty body)

**Response 200**:
```json
{
  "transactionId": "tx-abc123",
  "stationId": "station-001",
  "itemCount": 5,
  "totalAmount": 23.50,
  "startedAt": "2026-09-12T10:30:00Z",
  "completedAt": "2026-09-12T10:31:15Z",
  "lines": [
    { "sku": "SKU-000042", "name": "Item 42", "unitPrice": 7.85, "quantity": 2 },
    { "sku": "SKU-000007", "name": "Item 7", "unitPrice": 2.60, "quantity": 3 }
  ]
}
```

**Error 404**: `{ "error": "NOT_FOUND", "message": "No such transaction" }`
**Error 409** (not open): `{ "error": "TRANSACTION_NOT_OPEN", "message": "Transaction already finalized" }`
**Error 409** (empty): `{ "error": "EMPTY_BASKET", "message": "Cannot complete an empty transaction" }`

---

### 5. GET /transactions/{transactionId}

**Purpose**: Debug/instructor use. Not exercised by load client.

**Response 200**:
```json
{
  "transactionId": "tx-abc123",
  "stationId": "station-001",
  "status": "OPEN",
  "itemCount": 3,
  "runningTotal": 15.70,
  "startedAt": "2026-09-12T10:30:00Z"
}
```

**Error 404**: `{ "error": "NOT_FOUND", "message": "No such transaction" }`

---

### 6. GET /inventory/low-stock

**Purpose**: List items below a stock threshold.

**Query params**: `?threshold=50` (optional, server default = 50)

**Response 200**:
```json
{
  "threshold": 50,
  "generatedAt": "2026-09-12T10:35:00Z",
  "alerts": [
    {
      "sku": "SKU-000001",
      "name": "Item 1",
      "currentStock": 12,
      "threshold": 50,
      "triggeredAt": "2026-09-12T10:35:00Z"
    }
  ]
}
```

---

### 7. GET /analytics/popular-items

**Purpose**: Most popular items in the current hopping window.

**Query params**: `?limit=10` (optional, default = 10)

**Response 200**:
```json
{
  "windowSize": 1000,
  "slideInterval": 500,
  "windowStart": 4500,
  "windowEnd": 5500,
  "computedAt": "2026-09-12T10:34:00Z",
  "items": [
    { "sku": "SKU-000001", "name": "Item 1", "scanCount": 114, "rank": 1 },
    { "sku": "SKU-000002", "name": "Item 2", "scanCount": 74, "rank": 2 }
  ]
}
```

## Error Response Format (All Endpoints)

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description"
}
```

| HTTP Status | Error Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing required field (e.g., `stationId`) |
| 404 | `NOT_FOUND` | Unknown transaction ID |
| 404 | `UNKNOWN_SKU` | SKU not in catalog |
| 409 | `TRANSACTION_NOT_OPEN` | Scan/complete on a non-OPEN transaction |
| 409 | `EMPTY_BASKET` | Complete with no items scanned |
