# Feature Specification: Service-Based Architecture (Coarse-Grained Domain Services)

**Feature Branch**: `003-service-based-architecture`

**Created**: 2026-10-12

**Status**: Draft

**Input**: "Refactor the self-checkout backend into distributed services based on business functions (domain services). Each service is its own deployable unit; all services share a single centralized database. Services should not communicate directly unless absolutely necessary — coupling occurs at the database-access layer."

## User Scenarios & Testing *(mandatory)*

The business behavior is unchanged from prior weeks — the same unmodified load client validates it. What changes is the **deployment topology**: the behavior is now delivered by several independently deployable domain services over one shared database.

### User Story 1 — Customer Completes a Purchase (Priority: P1)

A customer starts a transaction at a station, scans 1–20 items, and completes to pay. The receipt is correct and inventory is decremented by exactly the units purchased — even though the transaction lifecycle and the inventory table are now in (logically) different data domains.

**Why this priority**: The core checkout flow, and the one the service boundary puts most at risk (the stock decrement is the correctness invariant).

**Independent Test**: Start a transaction, scan 3 items, complete, verify receipt line items/quantities/total, and verify stock dropped by exactly the units purchased.

**Acceptance Scenarios**:

1. **Given** the seeded catalog and inventory, **When** a station starts, scans 5 items, and completes, **Then** the receipt has correct lines/quantities/prices/total and `inventory_stock` is decremented by exactly those quantities.
2. **Given** two stations race to buy the last unit of the same SKU, **When** both complete, **Then** exactly one succeeds; stock never goes negative; `initial_stock − final_stock` equals the completed line items for that SKU.
3. **Given** a completed transaction, **When** another scan/complete is sent for it, **Then** a 409 is returned.
4. **Given** an empty basket, **When** complete is attempted, **Then** a 409 is returned.

### User Story 2 — Store Monitors Low Inventory (Priority: P2)

A manager queries low-stock. The catalog-service (which owns both catalog and inventory data domains) returns all SKUs below the threshold with name and current stock.

**Acceptance Scenarios**:

1. **Given** some SKUs are below the default threshold (50), **When** `/inventory/low-stock` is queried, **Then** every such SKU is returned with sku, name, current stock, threshold, and timestamp.
2. **Given** a `?threshold=` override, **When** queried, **Then** only SKUs below that threshold are returned.

### User Story 3 — Store Analyzes Popular Items (Priority: P2)

An analyst queries popular items. The analytics-service maintains a hopping window (size 1000, slide 500) by reading the shared `scan_events` log that checkout writes — with no call between the two services.

**Acceptance Scenarios**:

1. **Given** >1000 scans, **When** `/analytics/popular-items` is queried, **Then** the top items by scan count in the latest window are returned with `windowSize`, `slideInterval`, `windowStart`, `windowEnd`, `computedAt`, and ranked `sku`/`name`/`scanCount`/`rank`.
2. **Given** `?limit=5`, **When** queried, **Then** at most 5 items are returned.

### User Story 4 — Catalog Retrieval at Startup (Priority: P1)

The load client fetches the full 2000-item catalog from the gateway once at startup; the catalog-service serves it.

**Acceptance Scenarios**:

1. **Given** the system is up, **When** `GET /items` is called through the gateway, **Then** all 2000 items are returned with sku/name/price.

### User Story 5 — Independent Deployability (Priority: P1)

Each domain service is a separately deployable unit. An operator can build, start, stop, restart, or scale one service without touching the others, and the system continues to satisfy the contract.

**Why this priority**: Independent deployability is the property that distinguishes this style from the previous single-unit weeks — it is what the assignment is testing.

**Independent Test**: `docker compose up -d` the stack; `docker compose restart checkout-service` and confirm the other services keep serving; `docker compose up --scale checkout-service=2` and confirm requests still succeed.

**Acceptance Scenarios**:

1. **Given** the running stack, **When** one service container is restarted, **Then** only its endpoints are briefly unavailable and the rest keep serving.
2. **Given** the running stack, **When** the load client runs against the gateway, **Then** the full run completes with 0% errors at default load.

### Edge Cases

- Unknown SKU on scan → 404 `ITEM_NOT_FOUND`. Unknown transaction → 404 `TRANSACTION_NOT_FOUND`.
- 100+ stations race the last unit of a SKU → exactly one decrement succeeds; stock never negative.
- Same SKU scanned repeatedly → one receipt line with aggregated quantity.
- Restart between runs → `docker compose down && up` gives a freshly reseeded database.
- Analytics queried before 1000 scans exist → valid response with an empty `items` list.

## Requirements *(mandatory)*

### Functional Requirements (contract — unchanged)

- **FR-001**: Serve 2000 catalog items (sku, name, price) via `GET /items`.
- **FR-002**: Start a transaction via `POST /transactions` (201), returning a unique id and OPEN status.
- **FR-003**: Scan one unit via `POST /transactions/{id}/items`, returning item details and running totals.
- **FR-004**: Complete via `POST /transactions/{id}/complete`, decrementing stock per unit, returning a receipt.
- **FR-005**: Stock decrement MUST be atomic — never negative; `initial − final` equals completed line items per SKU under any concurrent load.
- **FR-006**: 409 when scanning into / completing a non-OPEN transaction. **FR-007**: 409 on empty-basket complete.
- **FR-008**: 404 for unknown transaction or SKU. **FR-009**: 400 when `stationId` is missing.
- **FR-010**: Low-stock alerts below a configurable threshold via `GET /inventory/low-stock`.
- **FR-011/012**: Hopping-window popular items (size 1000, slide 500) via `GET /analytics/popular-items`, including window metadata and ranked items.
- **FR-013**: `GET /transactions/{id}` for debugging. **FR-016**: receipt lines aggregate duplicate SKUs.

### Architecture Requirements (new this week)

- **AR-001**: The system MUST be decomposed into coarse-grained **domain services** by business function (not fine-grained microservices).
- **AR-002**: Each service MUST be an **independently deployable unit** (its own jar + Docker image + container).
- **AR-003**: All services MUST use a **single centralized database**.
- **AR-004**: Services MUST NOT communicate directly with one another unless absolutely necessary; inter-domain coupling occurs at the **database-access layer**.
- **AR-005**: Shared database-access logic MUST be distributed deliberately — a minimal shared surface for cross-boundary tables, with single-owner tables kept private to their service.
- **AR-006**: The external HTTP contract (7 endpoints, synchronous, on a single base URL) MUST be unchanged so the unmodified load client applies.
- **AR-007**: The database schema MUST be re-initializable to the seeded state between runs.

### Key Entities

- **Catalog Item** (`catalog_items`) — sku, name, price. Owned by catalog-service; read model shared.
- **Inventory Stock** (`inventory_stock`) — sku, stock. Owned by catalog-service; decremented by checkout-service via the shared DAO.
- **Transaction** / **Line Item** (`transactions`, `transaction_line_items`) — private to checkout-service.
- **Scan Event** (`scan_events`) — written by checkout-service, read by analytics-service.
- **Popular Items Snapshot** (`popular_items_snapshot`) — private to analytics-service.

## Success Criteria *(mandatory)*

- **SC-001**: All 7 endpoints, routed through the gateway, satisfy the OpenAPI contract — the load client completes a full run with 0% errors.
- **SC-002**: Default load (10 stations, 60s): 0 errors; stock invariant holds for every SKU. Report saved as `quality-attribute-analysis/default.json`.
- **SC-003**: Stress load (100 stations, 120s): <1% errors; invariant still holds. Report saved as `quality-attribute-analysis/stressMode.json`.
- **SC-004**: Each service is independently buildable and deployable; restarting one does not break the others.
- **SC-005**: Popular-items reflects the Zipf-like scan distribution (top items clearly dominant); low-stock accurately reflects `inventory_stock`.

## Assumptions

- Docker Desktop and a JDK 21 toolchain are available; images build from host-built jars.
- Payment always succeeds (no gateway); transaction ids are `tx-<UUID>`.
- Window parameters (1000 / 500) and low-stock default (50) are server-side config, not API inputs.
- "Reseed between runs" = `docker compose down && docker compose up` (ephemeral DB container, init scripts re-run).
- Catalog and inventory are merged into one product-data service; three domain services is intentional for this domain's size.
