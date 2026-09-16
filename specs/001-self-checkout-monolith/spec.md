# Feature Specification: Self-Checkout Monolith (Spring Boot)

**Feature Branch**: `001-self-checkout-monolith`

**Created**: 2026-09-12

**Status**: Draft

**Input**: User description: "Build a monolithic Spring Boot implementation of the self-checkout supermarket API, using a 3-phase learn-build-improve workflow"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Customer Completes a Purchase (Priority: P1)

A customer walks up to a self-checkout station, starts a new transaction, scans between 1 and 20 items from the store catalog, and then completes the transaction to pay. The system records the purchase, decrements inventory for each purchased item, and returns a receipt.

**Why this priority**: This is the core checkout flow — the entire system exists to serve this journey. Every other feature depends on transactions completing successfully.

**Independent Test**: Start a transaction with a station ID, scan 3 items by SKU, complete the transaction, and verify the receipt shows the correct items, quantities, and total. Verify stock decreased by exactly the number of units purchased.

**Acceptance Scenarios**:

1. **Given** the system is running with a seeded catalog, **When** a station starts a transaction, scans 5 items, and completes, **Then** a receipt is returned with correct line items, quantities, prices, and total amount, and inventory is decremented by exactly the quantities purchased.
2. **Given** two stations simultaneously complete transactions that include the last unit of the same SKU, **When** both attempt to complete, **Then** only one succeeds in purchasing that unit — stock never goes negative, and `initial_stock - final_stock` equals total completed line items for that SKU.
3. **Given** a transaction is already completed, **When** another scan or complete request is sent for that transaction, **Then** the system returns a 409 conflict error.
4. **Given** a transaction has no items scanned, **When** the station attempts to complete it, **Then** the system returns a 409 error indicating an empty basket.

---

### User Story 2 - Store Monitors Low Inventory (Priority: P2)

A store manager queries the system to see which items are running low on stock so they can trigger restocking. The system returns all items whose current stock is below a configurable threshold.

**Why this priority**: Inventory visibility is essential for store operations and is a required endpoint exercised by the load-testing client after each run.

**Independent Test**: After several transactions deplete stock on certain items, query the low-stock endpoint and verify it returns those items with accurate current stock levels.

**Acceptance Scenarios**:

1. **Given** items have been purchased and some SKUs have stock below the default threshold (50), **When** the low-stock endpoint is queried, **Then** alerts are returned for every SKU below the threshold, each showing the SKU, name, current stock, threshold, and timestamp.
2. **Given** a custom threshold is provided as a query parameter, **When** the endpoint is queried, **Then** only items below that custom threshold are returned.

---

### User Story 3 - Store Analyzes Popular Items (Priority: P2)

A store analyst queries the system to see which items are most frequently scanned, based on a sliding (hopping) window of recent scan activity. This helps identify trending products.

**Why this priority**: Analytics is a required feature exercised by the load-testing client. It tests a different kind of data processing (aggregation over a sliding window) compared to transactional CRUD.

**Independent Test**: After many items have been scanned, query the popular-items endpoint and verify it returns the top N items ranked by scan count, with correct window metadata.

**Acceptance Scenarios**:

1. **Given** more than 1000 items have been scanned across transactions, **When** the popular-items endpoint is queried, **Then** the response includes the top 10 items by scan count within the most recent window of 1000 scans, with correct `windowStart`, `windowEnd`, `windowSize`, `slideInterval`, and `computedAt` metadata.
2. **Given** a custom limit is provided, **When** the endpoint is queried with `?limit=5`, **Then** only 5 items are returned.
3. **Given** the Zipf-weighted scan distribution from the load client, **When** popular items are retrieved, **Then** the top-ranked items consistently correspond to the most frequently scanned SKUs.

---

### User Story 4 - Catalog Retrieval at Startup (Priority: P1)

The load-testing client fetches the full item catalog once at startup to build its randomized scan generator. The catalog must return all 2000 items with their SKU, name, and price.

**Why this priority**: The client cannot function without the catalog — this is the first call made and a blocker for all other operations.

**Independent Test**: Call GET /items and verify 2000 items are returned, each with a valid SKU, name, and price.

**Acceptance Scenarios**:

1. **Given** the system is running with a seeded catalog of 2000 items, **When** the catalog endpoint is called, **Then** all 2000 items are returned with `sku`, `name`, and `price` fields.

---

### User Story 5 - Developer Hand-Codes, Then AI Improves (Priority: P1)

This project follows a 3-phase learning workflow. The developer first reads Spring Boot documentation to understand the framework. Then they hand-code a barebones working implementation. Finally, AI reviews and improves the code to production quality.

**Why this priority**: The learning objective is as important as the deliverable — the developer must understand what the code does, not just have working code.

**Independent Test**: Phase 1 is complete when the developer can explain the role of @RestController, @Entity, @Repository, @Service, and @Transactional. Phase 2 is complete when the load client runs successfully against the hand-coded server. Phase 3 is complete when the code follows Spring Boot best practices and passes stress testing.

**Acceptance Scenarios**:

1. **Given** the developer has not started coding, **When** they complete Phase 1 reading, **Then** they can describe how a request flows from controller to service to repository to database and back.
2. **Given** a hand-coded barebones implementation (Phase 2), **When** the load client runs with default parameters (10 stations, 60s), **Then** all transactions complete with 0% error rate and the correctness invariant holds.
3. **Given** Phase 2 code is working, **When** AI reviews and improves it (Phase 3), **Then** the code uses proper Spring Boot layered architecture, idiomatic patterns, and passes stress testing (100 stations, 120s) with the correctness invariant intact.

---

### Edge Cases

- What happens when a scan request references a SKU that does not exist in the catalog? The system returns 404 with `UNKNOWN_SKU`.
- What happens when a transaction ID in a request does not exist? The system returns 404 with `NOT_FOUND`.
- What happens when 100+ stations simultaneously attempt to buy the last unit of the same item? Exactly one decrement succeeds; all others see the item at 0 stock. Stock never goes negative.
- What happens when the same SKU is scanned multiple times in one transaction? Each scan adds one unit to the basket; the receipt aggregates them into a single line with the total quantity.
- What happens when the system is restarted between test runs? The database must be easily re-seeded to the initial state (2000 items, 10000 stock each).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST serve a catalog of 2000 items, each with a unique SKU, name, and price, via `GET /items`.
- **FR-002**: System MUST allow starting a transaction by providing a station ID, returning a unique transaction ID and OPEN status, via `POST /transactions` (201 response).
- **FR-003**: System MUST allow scanning one unit of an item into an open transaction by SKU, returning the item details and running totals, via `POST /transactions/{id}/items`.
- **FR-004**: System MUST complete an open transaction, decrementing stock for each scanned unit atomically, and returning a receipt with line items, via `POST /transactions/{id}/complete`.
- **FR-005**: Stock decrement MUST be atomic — stock MUST NOT go negative, and `initial_stock - final_stock` MUST equal total completed line items per SKU under any concurrent load.
- **FR-006**: System MUST return 409 when attempting to scan into or complete a transaction that is not OPEN.
- **FR-007**: System MUST return 409 when attempting to complete a transaction with an empty basket.
- **FR-008**: System MUST return 404 for unknown transaction IDs or unknown SKUs.
- **FR-009**: System MUST return 400 when a transaction start request is missing the `stationId` field.
- **FR-010**: System MUST report low-stock alerts for all SKUs below a configurable threshold via `GET /inventory/low-stock`.
- **FR-011**: System MUST track the most popular items using a hopping window (window size: 1000 scans, slide interval: 500 scans) and serve results via `GET /analytics/popular-items`.
- **FR-012**: Popular items results MUST include `windowSize`, `slideInterval`, `windowStart`, `windowEnd`, `computedAt`, and a ranked list of items with `sku`, `name`, `scanCount`, and `rank`.
- **FR-013**: System MUST allow querying transaction status via `GET /transactions/{id}` for debugging.
- **FR-014**: All data (catalog, inventory, transactions, popular items snapshots) MUST be persisted in a relational database.
- **FR-015**: Database MUST be easily re-initialized between test runs (e.g., via a seed script or Spring Boot's `data.sql`).
- **FR-016**: Receipt lines MUST aggregate multiple scans of the same SKU into a single line with the correct quantity.

### Key Entities

- **Catalog Item**: A product available in the store. Attributes: SKU (unique identifier), name, price, current stock level.
- **Transaction**: A checkout session at a station. Attributes: unique ID, station ID, status (OPEN/COMPLETED/CANCELLED), start time, completion time.
- **Transaction Line Item**: An item scanned during a transaction. Attributes: associated transaction, SKU, unit price, quantity.
- **Scan Event**: A record of each individual item scan, used for the analytics sliding window. Attributes: global sequence number, SKU, timestamp.
- **Popular Items Snapshot**: A persisted computation of the top items within a window. Attributes: window boundaries, computation time, ranked list of SKUs with scan counts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 7 endpoints return correctly structured responses matching the OpenAPI contract, verified by the load-testing client completing a full run with 0% error rate.
- **SC-002**: Under default load (10 concurrent stations, 60 seconds), the system completes transactions at a sustained rate with 0 errors and the stock correctness invariant holds for every SKU.
- **SC-003**: Under stress load (100 concurrent stations, 120 seconds), the system remains functional with an error rate below 1% and the stock correctness invariant still holds.
- **SC-004**: The popular-items response reflects a Zipf-like distribution consistent with the load client's scan pattern, with the top 3 items clearly dominant in scan count.
- **SC-005**: Low-stock alerts accurately reflect current inventory state — every SKU below the threshold appears, and no SKU at or above the threshold is listed.
- **SC-006**: The developer (Phase 2) can explain the request flow through the Spring Boot layers and the purpose of each annotation used.
- **SC-007**: After Phase 3 improvements, code follows Spring Boot conventions: layered architecture (controller/service/repository), proper use of `@Transactional`, and no business logic in controllers.

## Learning Roadmap (3-Phase Approach)

### Phase 1 — Learn (Developer Reads Documentation)

Before writing any code, read and understand these Spring Boot concepts (minimum for a pure monolith):

1. **Project Setup**: Spring Initializr — how to bootstrap a Spring Boot project with the right dependencies (Spring Web, Spring Data JPA, PostgreSQL Driver)
2. **REST Controllers**: `@RestController`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@ResponseStatus`
3. **Data Layer**: `@Entity`, `@Id`, `@GeneratedValue`, `@Column`, `@Table` — how JPA maps Java objects to database tables
4. **Repositories**: `JpaRepository<Entity, ID>` — how Spring Data auto-generates CRUD operations; custom `@Query` for native SQL
5. **Configuration**: `application.yml` — database connection, JPA/Hibernate settings, server port
6. **Data Seeding**: `data.sql` — how Spring Boot auto-runs SQL at startup to seed the catalog

**Skip for Week 1** (these are Week 2 / Layered Architecture topics): `@Service`, `@ControllerAdvice`, DTOs, `@Lock(PESSIMISTIC_WRITE)`

### Phase 2 — Build (Developer Hand-Codes Barebones Version)

Write the initial implementation by hand. The goal is **functionally correct, not pretty**. This is a pure monolith — all logic in one controller, flat package structure, no layers.

1. Generate a Spring Boot project with Spring Initializr (Spring Web, Spring Data JPA, PostgreSQL)
2. Define 5 entity classes: `CatalogItem`, `Transaction`, `TransactionLineItem`, `ScanEvent`, `PopularItemSnapshot`
3. Define 5 repository interfaces extending `JpaRepository` with custom `@Query` methods
4. Write **one** `CheckoutController.java` implementing all 7 endpoints with all business logic inline
5. Create `data.sql` to seed 2000 catalog items with 10000 stock each
6. Test manually with `curl`, then run the load client with default parameters
7. **Checkpoint**: load client runs with 0% error rate at 10 stations

### Phase 3 — Polish (AI Helps with Correctness, Not Architecture)

After Phase 2 works, AI assists with **correctness hardening only** — no architectural changes (those are Week 2):

1. **Concurrency hardening**: Verify atomic stock decrement correctness under 100+ stations
2. **Analytics correctness**: Ensure hopping window recomputes every 500 scans properly
3. **Receipt formatting**: Properly aggregate duplicate SKUs with quantities
4. **Error responses**: Match the exact JSON error format from the OpenAPI spec
5. **Stress testing**: Run at 100 stations / 120s, diagnose and fix failures

## Assumptions

- PostgreSQL is installed and accessible locally (or via Docker) for development
- Java 21 JDK is installed (confirmed: version 21.0.5)
- The developer has basic Java knowledge but is new to Spring Boot specifically
- Maven will be used as the build tool (more documentation available than Gradle for beginners)
- The server will run on port 8080 (matching the load client's default `--baseUrl`)
- Payment always succeeds — there is no payment gateway integration
- Transaction IDs are generated server-side (UUIDs or sequential IDs)
- The low-stock default threshold is 50 (matching the mock server's default)
- The hopping window parameters (size=1000, interval=500) are hardcoded server-side, not configurable via API
- "Easily re-initializable" means dropping and recreating tables with seed data via Spring Boot's `spring.jpa.hibernate.ddl-auto=create` + `data.sql`
