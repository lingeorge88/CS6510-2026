# Implementation Plan: Self-Checkout Monolith

**Branch**: `001-self-checkout-monolith` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-self-checkout-monolith/spec.md`

## Summary

Build a **pure monolithic** Spring Boot backend implementing the self-checkout supermarket API (7 endpoints). "Monolithic" here means a single deployable unit with a flat internal structure — no enforced layers, no service/repository separation. Business logic lives directly in the controller. The layered refactoring (controller → service → repository) is **Week 2's assignment**.

PostgreSQL stores catalog, inventory, transactions, and analytics data. The critical challenge is atomic stock decrement under concurrent load (10–100+ stations).

## Architecture Style: Pure Monolith

Per Richards (*Fundamentals of Software Architecture*), a monolith is defined by its **deployment topology** (single unit, single process) and **minimal internal structure**. This is distinct from a layered architecture, which enforces horizontal separation of concerns within the same deployment unit.

For Week 1:
- **One controller class** handles all 7 endpoints with business logic inline
- **Entity classes** define the database schema (JPA requires these as separate files)
- **Repository interfaces** provide data access (Spring Data JPA requires these)
- **No service layer** — logic lives in the controller
- **No DTOs** — entities and Maps returned directly
- **No exception handler class** — errors handled inline

Week 2 (Layered) will refactor this into proper layers.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.3.x (Spring Web, Spring Data JPA, PostgreSQL Driver)

**Storage**: PostgreSQL 15+ (local install or Docker)

**Testing**: Load-testing client provided (Java, zero dependencies); manual curl testing during development

**Target Platform**: Local development (macOS), server on localhost:8080

**Project Type**: Web service (pure monolith)

**Performance Goals**: 10 concurrent stations with 0% error rate; 100 stations with <1% error rate and stock correctness invariant maintained

**Constraints**: All endpoints synchronous; stock must never go negative; hopping window analytics must be persisted; database must be re-seedable between runs

**Scale/Scope**: 2000 catalog items, 10000 stock per item, 10–100 concurrent stations, 60–120 second test runs

## Constitution Check

*GATE: No project constitution defined — no gates to check. Proceeding.*

## Project Structure

### Documentation (this feature)

```text
specs/001-self-checkout-monolith/
├── spec.md
├── plan.md              # This file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api-endpoints.md
└── tasks.md             # From /speckit-tasks
```

### Source Code (repository root)

```text
monolith/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/selfcheckout/
│   │   │   ├── SelfCheckoutApplication.java    # ~10 lines  — Spring Boot main
│   │   │   ├── CheckoutController.java         # ~300 lines — ALL 7 endpoints + all business logic
│   │   │   ├── CatalogItem.java                # ~30 lines  — @Entity
│   │   │   ├── Transaction.java                # ~40 lines  — @Entity
│   │   │   ├── TransactionLineItem.java        # ~25 lines  — @Entity
│   │   │   ├── ScanEvent.java                  # ~20 lines  — @Entity
│   │   │   ├── PopularItemSnapshot.java        # ~30 lines  — @Entity
│   │   │   ├── CatalogItemRepository.java      # ~15 lines  — JpaRepository + custom queries
│   │   │   ├── TransactionRepository.java      # ~5 lines   — JpaRepository
│   │   │   ├── TransactionLineItemRepository.java  # ~10 lines
│   │   │   ├── ScanEventRepository.java        # ~10 lines
│   │   │   └── PopularItemSnapshotRepository.java  # ~10 lines
│   │   └── resources/
│   │       ├── application.yml                 # ~15 lines  — DB config
│   │       └── data.sql                        # ~2000 lines — seed catalog
│   └── test/  (optional)
└── README.md
```

**~12 Java files, ~500 lines of hand-written Java + generated seed SQL.**

All files live flat in one package — no sub-packages. This is intentionally un-layered. The controller directly `@Autowired`s repositories and does everything inline.

**Structure Decision**: Flat monolith. One controller, entity classes (required by JPA), repository interfaces (required by Spring Data). No services, no DTOs, no exception handlers, no sub-packages. This is the "everything in one place" monolith that Week 2's layered architecture will refactor.

## Phase Breakdown: Learn → Build → Improve

### Phase 1 — Learn (Developer reads documentation)

No code is written. Focus on the minimum needed for the monolith:

| Topic | What to Learn | Key Concepts |
|---|---|---|
| Project Bootstrap | Spring Initializr, `pom.xml`, project layout | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql` |
| REST Controllers | How HTTP requests map to Java methods | `@RestController`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@ResponseStatus` |
| JPA Entities | How Java classes map to database tables | `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column` |
| Repositories | How Spring Data generates queries | `JpaRepository<T, ID>`, `@Query`, `@Modifying`, `@Transactional` |
| Configuration | Database connection, JPA settings | `application.yml`, `spring.datasource.*`, `spring.jpa.*` |
| Data Seeding | How to load initial data on startup | `data.sql`, `spring.jpa.hibernate.ddl-auto=create` |

**Skip for now** (Week 2 topics): `@Service`, constructor injection, `@ControllerAdvice`, DTOs, `@Lock(PESSIMISTIC_WRITE)`

**Completion check**: Can the developer explain: HTTP request → `@RestController` method → `@Autowired` repository → database → return entity as JSON?

### Phase 2 — Build (Developer hand-codes)

Write all code by hand. **Goal: works, simple, correct. Ugly is fine.**

#### Build order:

1. **Spring Initializr** → generate project with Spring Web, Spring Data JPA, PostgreSQL Driver → unzip into `monolith/`
2. **application.yml** → PostgreSQL connection, `ddl-auto=create`, port 8080
3. **Entity classes** (5 files) → `CatalogItem`, `Transaction`, `TransactionLineItem`, `ScanEvent`, `PopularItemSnapshot`. Just fields + JPA annotations.
4. **Repository interfaces** (5 files) → extend `JpaRepository`. Add `@Query` for stock decrement and analytics.
5. **CheckoutController.java** (1 file) → all 7 endpoints, all business logic. Wire repositories with `@Autowired`.
6. **data.sql** → 2000 INSERT statements for catalog items (generate with a script)
7. **Test with curl** → hit each endpoint manually
8. **Run load client** → `./run.sh --stations=10 --duration=60`

#### What's OK in Week 1:
- All logic in one controller — that's the point of a monolith
- Returning entities directly as JSON (Jackson handles it)
- Inline error handling with `ResponseEntity`
- Hardcoded values (threshold=50, windowSize=1000)
- Verbose, repetitive code

### Phase 3 — AI Assist (still Week 1, polish only)

After Phase 2 passes the load client, AI helps with **correctness hardening**, not architecture changes:

| Area | What AI Helps With |
|---|---|
| **Stock decrement** | Verify the atomic UPDATE is correct under 100 stations |
| **Popular items** | Ensure hopping window logic is right (recompute every 500 scans) |
| **Receipt lines** | Properly aggregate duplicate SKUs with quantity |
| **Error responses** | Match the exact `{ "error": "CODE", "message": "..." }` format |
| **Stress testing** | Run 100 stations / 120s, diagnose failures, tune connection pool |

**Not in scope for Phase 3 / Week 1**: No service layer extraction, no DTOs, no `@ControllerAdvice`, no sub-packages — those are Week 2.

## Key Design Decisions

### 1. Stock Decrement Strategy
**Decision**: Single atomic SQL UPDATE with a WHERE guard
```sql
UPDATE catalog_items SET stock = stock - 1 WHERE sku = :sku AND stock > 0
```
**Rationale**: PostgreSQL row-level lock during UPDATE ensures exactly-once decrement. Check return value (rows affected) — if 0, stock was already at 0.

### 2. Transaction ID Generation
**Decision**: `"tx-" + UUID.randomUUID().toString()`
**Rationale**: No sequence contention, globally unique.

### 3. Popular Items Hopping Window
**Decision**: Store scans in `scan_events` with a monotonic `global_seq`. Every 500 scans, aggregate last 1000 and persist to `popular_items_snapshot`. API reads latest snapshot.
**Implementation**: Use a DB sequence for `global_seq`. In the controller's scan method, after inserting the scan event, check if `global_seq % 500 == 0` and trigger recomputation.

### 4. Data Seeding
**Decision**: `spring.jpa.hibernate.ddl-auto=create` + `data.sql`
**Rationale**: Drops and recreates tables on every startup. Fresh state for each test run.

### 5. Flat Package Structure
**Decision**: All Java files in one package (`com.selfcheckout`), no sub-packages.
**Rationale**: This is a pure monolith. Internal organization is not the point this week. Week 2 introduces layered structure.

## Complexity Tracking

No constitution violations to track — no constitution defined.
