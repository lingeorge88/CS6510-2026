# Data Model: Self-Checkout Monolith

**Date**: 2026-09-12 | **Plan**: [plan.md](plan.md)

## Entity Relationship Overview

```
catalog_items  1──────∞  transaction_line_items  ∞──────1  transactions
     │
     │ (sku referenced by)
     │
scan_events ───(aggregated into)───> popular_items_snapshot
```

## Entities

### 1. CatalogItem

Represents a product in the store's catalog with current stock level.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `sku` | `VARCHAR(20)` | **PK** | Format: `SKU-NNNNNN` |
| `name` | `VARCHAR(255)` | NOT NULL | |
| `price` | `DECIMAL(10,2)` | NOT NULL | |
| `stock` | `INTEGER` | NOT NULL, DEFAULT 0 | Decremented atomically at transaction completion. Must never go negative. |

**Critical operation**: `UPDATE catalog_items SET stock = stock - 1 WHERE sku = ? AND stock > 0`

---

### 2. Transaction

Represents a checkout session at a station.

| Java Field | DB Column | Type | Constraints | Notes |
|---|---|---|---|---|
| `transactionId` | `transaction_id` | `VARCHAR(50)` | **PK** | Format: `tx-UUID`. Matches OpenAPI `transactionId` field name. |
| `stationId` | `station_id` | `VARCHAR(50)` | NOT NULL | Identifies the physical checkout station |
| `status` | `status` | `VARCHAR(20)` | NOT NULL, DEFAULT 'OPEN' | Enum: `OPEN`, `COMPLETED`, `CANCELLED` |
| `itemCount` | `item_count` | `INTEGER` | NOT NULL, DEFAULT 0 | Running count of scanned items |
| `runningTotal` | `running_total` | `DECIMAL(10,2)` | NOT NULL, DEFAULT 0.00 | Running price total |
| `startedAt` | `started_at` | `TIMESTAMP` | NOT NULL | Set on creation |
| `completedAt` | `completed_at` | `TIMESTAMP` | NULLABLE | Set on completion |

**State transitions**: `OPEN` → `COMPLETED` (on complete) or `OPEN` → `CANCELLED` (not used by client but supported)

**JSON note**: Java field names (camelCase) match the OpenAPI spec directly. Use `@Column(name="snake_case")` for DB column mapping.

---

### 3. TransactionLineItem

Represents one scanned item unit within a transaction. Each scan creates one row.

| Java Field | DB Column | Type | Constraints | Notes |
|---|---|---|---|---|
| `id` | `id` | `BIGSERIAL` | **PK** | Auto-increment |
| `transactionId` | `transaction_id` | `VARCHAR(50)` | NOT NULL | References transactions.transaction_id |
| `sku` | `sku` | `VARCHAR(20)` | NOT NULL | References catalog_items.sku |
| `unitPrice` | `unit_price` | `DECIMAL(10,2)` | NOT NULL | Captured at scan time |

**Receipt generation**: Group by `(transactionId, sku)`, count rows for `quantity`, use `unitPrice` from any row (same item = same price).

**Relationship approach (Week 1)**: Use simple String fields for `transactionId` and `sku`. No JPA `@ManyToOne` — look up related entities manually in the controller.

---

### 4. ScanEvent

Append-only log of every item scan across all transactions. Used for the popular items hopping window.

| Java Field | DB Column | Type | Constraints | Notes |
|---|---|---|---|---|
| `id` | `id` | `BIGSERIAL` | **PK** | Auto-increment |
| `globalSeq` | `global_seq` | `BIGINT` | NOT NULL, UNIQUE | Monotonically increasing scan counter across all stations |
| `sku` | `sku` | `VARCHAR(20)` | NOT NULL | |
| `scannedAt` | `scanned_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | |

**Index**: `CREATE INDEX idx_scan_events_global_seq ON scan_events (global_seq DESC)` — for efficiently querying the last N scans.

**Hopping window trigger**: When `globalSeq % 500 == 0`, compute top items from last 1000 rows and write to `popular_items_snapshot`.

---

### 5. PopularItemSnapshot

Persisted result of a hopping window computation. Each computation writes N rows (one per ranked item).

| Java Field | DB Column | Type | Constraints | Notes |
|---|---|---|---|---|
| `id` | `id` | `BIGSERIAL` | **PK** | Auto-increment |
| `windowStart` | `window_start` | `BIGINT` | NOT NULL | `globalSeq` of oldest scan in this window |
| `windowEnd` | `window_end` | `BIGINT` | NOT NULL | `globalSeq` of newest scan in this window |
| `computedAt` | `computed_at` | `TIMESTAMP` | NOT NULL | When this snapshot was computed |
| `sku` | `sku` | `VARCHAR(20)` | NOT NULL | |
| `name` | `name` | `VARCHAR(255)` | NOT NULL | Denormalized from catalog for fast reads |
| `scanCount` | `scan_count` | `INTEGER` | NOT NULL | Number of scans of this SKU within the window |
| `rank` | `rank` | `INTEGER` | NOT NULL | 1-based rank by scanCount descending |

**Querying**: The API reads the most recent snapshot: `SELECT * FROM popular_items_snapshot WHERE window_end = (SELECT MAX(window_end) FROM popular_items_snapshot) ORDER BY rank`

## Database Initialization

On startup with `spring.jpa.hibernate.ddl-auto=create`:
1. Hibernate drops and recreates all tables from entity definitions
2. `data.sql` runs and inserts 2000 catalog items with stock = 10000 each
3. All other tables start empty

The `data.sql` file contains:
```sql
-- Generate 2000 catalog items: SKU-000001 through SKU-002000
-- Each with stock = 10000, price varies deterministically
INSERT INTO catalog_items (sku, name, price, stock) VALUES
('SKU-000001', 'Item 1', 0.85, 10000),
('SKU-000002', 'Item 2', 1.20, 10000),
...
('SKU-002000', 'Item 2000', ..., 10000);
```

Price formula (matching mock server): `ROUND(0.5 + (i % 47) * 0.35, 2)`

## Validation Rules

| Entity | Rule | Enforced By |
|---|---|---|
| CatalogItem.stock | Must never go negative | SQL WHERE guard in UPDATE |
| Transaction.status | Only OPEN transactions can be scanned into or completed | Service layer check before operation |
| Transaction.basket | Cannot complete with 0 items | Service layer check |
| ScanEvent.global_seq | Must be monotonically increasing | DB sequence or application AtomicLong |
| TransactionLineItem.sku | Must exist in catalog | Service layer lookup before insert |
