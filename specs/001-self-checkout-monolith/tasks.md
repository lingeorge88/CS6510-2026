# Tasks: Self-Checkout Monolith (Week 1)

**Input**: Design documents from `specs/001-self-checkout-monolith/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/api-endpoints.md, research.md, quickstart.md

**Tests**: Not required for Week 1. Validation is via the provided load-testing client.

**Organization**: Tasks follow the 3-phase learning approach: Phase 1 (learn), Phase 2 (hand-code), Phase 3 (AI polish). Within Phase 2, tasks are ordered by dependency so each builds on the last.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)
- All source paths relative to `monolith/src/main/java/com/selfcheckout/`
- All resource paths relative to `monolith/src/main/resources/`

---

## Phase 1: Learn (Read Documentation Before Coding)

**Purpose**: Understand Spring Boot fundamentals before writing any code. No code is written in this phase.

- [ ] T001 Read Spring Initializr docs: understand how to generate a project with Spring Web, Spring Data JPA, and PostgreSQL Driver dependencies. Visit https://start.spring.io/ and explore the options.
- [ ] T002 Read `@RestController` docs: understand `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@ResponseStatus`, and how Spring converts return values to JSON via Jackson.
- [ ] T003 Read JPA Entity docs: understand `@Entity`, `@Table`, `@Id`, `@GeneratedValue(strategy)`, `@Column(name, nullable, length)`, and how Hibernate maps Java classes to PostgreSQL tables.
- [ ] T004 Read Spring Data JPA Repository docs: understand `JpaRepository<T, ID>`, derived query methods (e.g., `findByStatus`), `@Query` for custom JPQL/native SQL, `@Modifying` for UPDATE/DELETE queries, and `@Transactional`.
- [ ] T005 Read `application.yml` configuration docs: understand `spring.datasource.url/username/password`, `spring.jpa.hibernate.ddl-auto=create`, `spring.sql.init.mode=always`, and `server.port`.
- [ ] T006 Read the OpenAPI spec at `spec/self-checkout-openapi.yaml` and the API contract reference at `specs/001-self-checkout-monolith/contracts/api-endpoints.md`. Understand every endpoint's request/response shape and error codes.
- [ ] T007 Read the mock server at `mockserver/MockServer.java` — understand how it implements each endpoint, especially the `AtomicInteger.updateAndGet()` stock decrement at line 207 and the `recentScans` deque for popular items.

**Checkpoint**: You can explain: HTTP request → `@RestController` method → `@Autowired` repository → database → return object as JSON. You understand the API contract and the mock server's behavior.

---

## Phase 2: Hand-Code (Build the Monolith)

**Purpose**: Write the full implementation by hand. All logic in one controller. Ugly is fine — correctness matters.

### 2A: Project Setup

- [ ] T008 Generate a Spring Boot project via https://start.spring.io/ with: Maven, Java 21, Spring Boot 3.3.x, Group=`com.selfcheckout`, Artifact=`monolith`, Dependencies=[Spring Web, Spring Data JPA, PostgreSQL Driver]. Unzip into `monolith/` at the repo root.
- [ ] T009 Configure `monolith/src/main/resources/application.yml` with PostgreSQL connection (`jdbc:postgresql://localhost:5432/selfcheckout`), `spring.jpa.hibernate.ddl-auto: create`, `spring.sql.init.mode: always`, `server.port: 8080`.
- [ ] T010 Create the PostgreSQL database: run `createdb selfcheckout` (or equivalent). Verify connection with `psql selfcheckout -c "SELECT 1"`.
- [ ] T011 Verify the skeleton runs: `cd monolith && ./mvnw spring-boot:run` should start on port 8080 (will show errors about missing entities — that's expected at this point, just verify the app boots).

**Checkpoint**: Spring Boot app starts and connects to PostgreSQL.

---

### 2B: Entity Classes (all [P] — independent files)

- [ ] T012 [P] [US4] Create `CatalogItem.java` entity in `monolith/src/main/java/com/selfcheckout/CatalogItem.java`. Fields: `sku` (VARCHAR(20), PK), `name` (VARCHAR(255), NOT NULL), `price` (DECIMAL(10,2), NOT NULL), `stock` (INTEGER, NOT NULL, default 0). Use `@Entity`, `@Table(name="catalog_items")`, `@Id` on sku (no `@GeneratedValue` — sku is assigned).
- [ ] T013 [P] [US1] Create `Transaction.java` entity in `monolith/src/main/java/com/selfcheckout/Transaction.java`. Fields: `id` (VARCHAR(50), PK — assigned, not generated), `stationId` (VARCHAR(50), NOT NULL), `status` (VARCHAR(20), NOT NULL, default "OPEN"), `itemCount` (INTEGER, default 0), `runningTotal` (DECIMAL(10,2), default 0.00), `startedAt` (TIMESTAMP, NOT NULL), `completedAt` (TIMESTAMP, nullable).
- [ ] T014 [P] [US1] Create `TransactionLineItem.java` entity in `monolith/src/main/java/com/selfcheckout/TransactionLineItem.java`. Fields: `id` (BIGSERIAL, PK, `@GeneratedValue(strategy=IDENTITY)`), `transactionId` (VARCHAR(50), NOT NULL), `sku` (VARCHAR(20), NOT NULL), `unitPrice` (DECIMAL(10,2), NOT NULL).
- [ ] T015 [P] [US3] Create `ScanEvent.java` entity in `monolith/src/main/java/com/selfcheckout/ScanEvent.java`. Fields: `id` (BIGSERIAL, PK, `@GeneratedValue(strategy=IDENTITY)`), `globalSeq` (BIGINT, NOT NULL, UNIQUE), `sku` (VARCHAR(20), NOT NULL), `scannedAt` (TIMESTAMP, NOT NULL, default NOW()).
- [ ] T016 [P] [US3] Create `PopularItemSnapshot.java` entity in `monolith/src/main/java/com/selfcheckout/PopularItemSnapshot.java`. Fields: `id` (BIGSERIAL, PK, `@GeneratedValue(strategy=IDENTITY)`), `windowStart` (BIGINT, NOT NULL), `windowEnd` (BIGINT, NOT NULL), `computedAt` (TIMESTAMP, NOT NULL), `sku` (VARCHAR(20), NOT NULL), `name` (VARCHAR(255), NOT NULL), `scanCount` (INTEGER, NOT NULL), `rank` (INTEGER, NOT NULL).

**Checkpoint**: App starts with `ddl-auto=create` and generates all 5 tables in PostgreSQL.

---

### 2C: Seed Data

- [ ] T017 [US4] Create `monolith/src/main/resources/data.sql` to insert 2000 catalog items. Each item: `SKU-NNNNNN` (zero-padded), `Item N`, price = `ROUND(0.5 + (N % 47) * 0.35, 2)`, stock = 10000. Generate the 2000 INSERT statements programmatically (use a script or AI to produce the SQL). Verify by starting the app and running `curl http://localhost:8080/items` — should eventually return 2000 items once the controller exists.

**Checkpoint**: App starts, `data.sql` runs, `SELECT COUNT(*) FROM catalog_items` returns 2000.

---

### 2D: Repository Interfaces (all [P] — independent files)

- [ ] T018 [P] [US4] Create `CatalogItemRepository.java` in `monolith/src/main/java/com/selfcheckout/CatalogItemRepository.java`. Extends `JpaRepository<CatalogItem, String>`. Add `@Modifying @Transactional @Query("UPDATE CatalogItem c SET c.stock = c.stock - 1 WHERE c.sku = :sku AND c.stock > 0")` method `int decrementStock(@Param("sku") String sku)` — returns number of rows affected (1 = success, 0 = out of stock). Add `List<CatalogItem> findByStockLessThan(int threshold)` for low-stock queries.
- [ ] T019 [P] [US1] Create `TransactionRepository.java` in `monolith/src/main/java/com/selfcheckout/TransactionRepository.java`. Extends `JpaRepository<Transaction, String>`.
- [ ] T020 [P] [US1] Create `TransactionLineItemRepository.java` in `monolith/src/main/java/com/selfcheckout/TransactionLineItemRepository.java`. Extends `JpaRepository<TransactionLineItem, Long>`. Add `List<TransactionLineItem> findByTransactionId(String transactionId)`.
- [ ] T021 [P] [US3] Create `ScanEventRepository.java` in `monolith/src/main/java/com/selfcheckout/ScanEventRepository.java`. Extends `JpaRepository<ScanEvent, Long>`. Add a native query to find the top N SKUs by count from the last `windowSize` scan events.
- [ ] T022 [P] [US3] Create `PopularItemSnapshotRepository.java` in `monolith/src/main/java/com/selfcheckout/PopularItemSnapshotRepository.java`. Extends `JpaRepository<PopularItemSnapshot, Long>`. Add query to find all snapshots with the maximum `windowEnd` value (latest snapshot).

**Checkpoint**: App starts with no errors. All repositories are wired.

---

### 2E: The Controller (one file, all logic)

- [ ] T023 [US4] Create `CheckoutController.java` in `monolith/src/main/java/com/selfcheckout/CheckoutController.java`. Start with `@RestController` and `@Autowired` all 5 repositories. Implement `GET /items` — return `{ "items": [...] }` with all catalog items. Test with `curl http://localhost:8080/items`.
- [ ] T024 [US1] Add `POST /transactions` to `CheckoutController.java`. Read `stationId` from request body. Validate it's not null/blank (return 400 if missing). Generate transaction ID as `"tx-" + UUID.randomUUID()`. Create and save a `Transaction` entity with status=OPEN, itemCount=0, runningTotal=0.0, startedAt=now. Return 201 with the transaction JSON. Test with `curl -X POST http://localhost:8080/transactions -H "Content-Type: application/json" -d '{"stationId":"station-001"}'`.
- [ ] T025 [US1] Add `POST /transactions/{transactionId}/items` to `CheckoutController.java`. Look up transaction by ID (404 if not found). Check status is OPEN (409 if not). Look up catalog item by SKU from request body (404 if unknown SKU). Create and save a `TransactionLineItem`. Update transaction's `itemCount` and `runningTotal`. Save a `ScanEvent` with a monotonic global sequence number (use an `AtomicLong` field in the controller). Check if hopping window recomputation is needed (`globalSeq % 500 == 0` and `globalSeq >= 1000`). Return scan result JSON. Test with curl.
- [ ] T026 [US1] Add `POST /transactions/{transactionId}/complete` to `CheckoutController.java`. Look up transaction (404 if not found). Check status OPEN (409 if not). Check basket not empty (409 if empty). Set status=COMPLETED, completedAt=now. For each line item, call `catalogItemRepository.decrementStock(sku)`. Build receipt lines by grouping line items by SKU (aggregate quantity). Return receipt JSON. Test with curl.
- [ ] T027 [US1] Add `GET /transactions/{transactionId}` to `CheckoutController.java`. Look up transaction (404 if not found). Return transaction status JSON. Test with curl.
- [ ] T028 [US2] Add `GET /inventory/low-stock` to `CheckoutController.java`. Read optional `threshold` query param (default 50). Query `catalogItemRepository.findByStockLessThan(threshold)`. Build and return response with `threshold`, `generatedAt`, and `alerts` array. Test with curl.
- [ ] T029 [US3] Add `GET /analytics/popular-items` to `CheckoutController.java`. Read optional `limit` query param (default 10). Query the latest `PopularItemSnapshot` rows. Return response with `windowSize`, `slideInterval`, `windowStart`, `windowEnd`, `computedAt`, and `items` array. If no snapshots exist yet, return empty items with windowStart=0, windowEnd=0. Test with curl.
- [ ] T030 [US3] Implement the hopping window recomputation method in `CheckoutController.java`. Called from the scan endpoint (T025) when `globalSeq % 500 == 0`. Query the last 1000 scan events, group by SKU, count occurrences, rank by count descending, take top 10. Delete old snapshots (optional) and insert new `PopularItemSnapshot` rows. Wrap in `@Transactional`.

**Checkpoint**: All 7 endpoints work via curl. JSON response shapes match the OpenAPI contract.

---

### 2F: Load Client Validation

- [ ] T031 [US5] Run the load client with default parameters: `cd load-client && ./run.sh --baseUrl=http://localhost:8080 --stations=10 --duration=60`. Verify: 0% error rate, all transactions complete, low-stock alerts present, popular items returned. Save the JSON report for submission.
- [ ] T032 [US5] Verify stock correctness via SQL: `SELECT COUNT(*) FROM catalog_items WHERE stock < 0` should return 0. Run the stock invariant query from quickstart.md to verify `stock_consumed == total_sold` for all SKUs.

**Checkpoint**: Phase 2 complete. Load client passes with 0% error rate at default settings. Stock correctness invariant holds.

---

## Phase 3: AI Polish (Correctness Hardening)

**Purpose**: AI reviews the working code and fixes correctness issues. No architectural changes — those are Week 2.

- [ ] T033 AI reviews `CheckoutController.java` for concurrency bugs in the scan endpoint — verify the `AtomicLong` global sequence counter is thread-safe and the hopping window recomputation doesn't race with concurrent scans.
- [ ] T034 AI reviews the stock decrement logic — verify `decrementStock()` handles the case where multiple stations try to buy the last unit of the same SKU simultaneously. The UPDATE's WHERE guard should handle this, but verify no other code path bypasses it.
- [ ] T035 AI reviews receipt line aggregation — verify that when a transaction scans the same SKU multiple times, the receipt correctly groups them into one line with `quantity > 1`.
- [ ] T036 AI reviews all error responses — verify every error path returns `{ "error": "CODE", "message": "..." }` with the correct HTTP status code (400, 404, 409) matching the OpenAPI spec.
- [ ] T037 Run stress test: `cd load-client && ./run.sh --baseUrl=http://localhost:8080 --stations=100 --duration=120`. If errors occur, diagnose and fix. May need to tune `spring.datasource.hikari.maximum-pool-size` in `application.yml`. Save the JSON report for submission.
- [ ] T038 Verify stock correctness after stress test via the same SQL checks as T032.

**Checkpoint**: Stress test passes. Stock invariant holds under 100 concurrent stations.

---

## Phase 4: Deliverables

**Purpose**: Prepare submission artifacts.

- [ ] T039 Write `quality-attributes.md` at the repo root. List architectural characteristics (performance, reliability, scalability, availability, etc.) with concrete requirements for each. Identify top 3 priorities and explain trade-offs.
- [ ] T040 Rename/copy the two JSON report files to clearly identify them: one for default mode (10 stations, 60s), one for stress mode (100 stations, 120s). Ensure both are in `load-client/reports/`.
- [ ] T041 Commit all code, reports, and quality-attributes.md to the repo. Push to GitHub.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Learn)**: No dependencies — start immediately
- **Phase 2A (Setup)**: Depends on Phase 1 completion
- **Phase 2B (Entities)**: Depends on 2A — all entity tasks are parallel with each other
- **Phase 2C (Seed Data)**: Depends on T012 (CatalogItem entity)
- **Phase 2D (Repositories)**: Depends on 2B — all repo tasks are parallel with each other
- **Phase 2E (Controller)**: Depends on 2D — tasks are sequential (each endpoint builds on prior ones)
- **Phase 2F (Validation)**: Depends on 2E completion
- **Phase 3 (AI Polish)**: Depends on Phase 2F passing
- **Phase 4 (Deliverables)**: Depends on Phase 3 completion

### User Story Mapping

- **US1** (Customer Completes Purchase): T013, T014, T019, T020, T024, T025, T026, T027
- **US2** (Low Stock Monitoring): T028
- **US3** (Popular Items Analytics): T015, T016, T021, T022, T029, T030
- **US4** (Catalog Retrieval): T012, T017, T018, T023
- **US5** (3-Phase Learning Workflow): T001–T007, T031, T032

### Parallel Opportunities

```
Phase 2B (all parallel):  T012 | T013 | T014 | T015 | T016

Phase 2D (all parallel):  T018 | T019 | T020 | T021 | T022

Phase 3 (all parallel):   T033 | T034 | T035 | T036
```

---

## Implementation Strategy

### MVP First (US4 + US1)

1. Phase 1: Read docs
2. Phase 2A: Project setup
3. T012 + T017 + T018 + T023: Catalog entity + seed + repo + GET /items endpoint
4. T013 + T014 + T019 + T020: Transaction entities + repos
5. T024 → T025 → T026 → T027: Transaction lifecycle endpoints
6. **STOP and TEST**: Run load client at 10 stations — core flow should work

### Then Add Analytics (US2 + US3)

7. T028: Low-stock endpoint
8. T015 + T016 + T021 + T022: Scan event + popular items entities + repos
9. T029 + T030: Popular items endpoint + hopping window
10. **FULL TEST**: Run load client — all features should work

### Then Polish + Submit (Phase 3 + 4)

11. AI review + stress test
12. Write quality-attributes.md
13. Commit and push
