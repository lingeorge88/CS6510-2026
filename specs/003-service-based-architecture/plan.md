# Implementation Plan: Service-Based Architecture

**Branch**: `003-service-based-architecture` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Refactor the self-checkout backend into distributed **coarse-grained domain services** that share one centralized database, each an independently deployable unit, with coupling at the database-access layer rather than through direct service-to-service calls.

## Summary

Decompose the system by business function into **three coarse-grained domain services** plus an **API gateway**, all backed by a **single shared PostgreSQL database**. The services are packaged as independent Spring Boot jars and deployed as separate Docker containers via `docker-compose`. The external HTTP contract (the 7 OpenAPI endpoints on `http://localhost:8080`) is unchanged, so the same unmodified load client drives this week exactly as before.

The design question the assignment foregrounds — *how shared database-access logic is distributed across services* — is answered with a **thin `shared-persistence` library** that carries only the data-access coupling surface (the tables touched by more than one service), while each service keeps its single-owner tables private.

## Architecture Style: Service-Based

Per Richards & Ford (*Fundamentals of Software Architecture*, Ch. 13), service-based architecture is a distributed style defined by its topology: a set of **coarse-grained, independently deployed domain services** (typically 4–12, averaging ~7) sharing a **single monolithic database**. It is the deliberate middle ground between a monolith and microservices — you gain independent deployability and fault isolation at the service boundary, but keep one database so ordinary writes stay ACID (no sagas, no database-per-service, no service mesh).

This is distinct from the earlier weeks:
- **Monolith / Layered / Pipeline** — one deployable unit, one process. Internal structure varied; deployment did not.
- **Service-Based (this week)** — multiple deployable units, multiple processes, one shared database. Coupling moves from in-process method calls to the **database-access layer**.

We land at **3 domain services** (below the 4–12 band) because the domain is small and over-decomposing into fine-grained services is the explicitly wrong answer for this style. Catalog and Inventory are deliberately **merged** into one product-data service rather than split.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 4.1.1 (Spring WebMVC, Spring Data JPA, Validation), PostgreSQL driver, nginx (gateway)

**Storage**: One shared PostgreSQL 16 database (`selfcheckout`), schema owned by the database (created once by the container's init scripts); services run `ddl-auto: none`

**Testing**: The provided load client (unmodified); the stock-correctness invariant check; manual curl

**Target Platform**: Docker Desktop on macOS; `docker-compose` orchestrating 5 containers (db + 3 services + gateway)

**Project Type**: Distributed web service (service-based)

**Performance Goals**: 10 stations, 0% error rate, invariant holds; 100 stations / 120s, <1% error rate, invariant still holds

**Constraints**: External contract unchanged; every endpoint synchronous; stock never negative; each service independently deployable; **no direct service-to-service calls** (coupling at the DB layer only)

**Scale/Scope**: 2000 catalog items, 10000 stock each, 10–100 concurrent stations, 60–120s runs

## Constitution Check

*GATE: No project constitution defined — no gates to check. Proceeding.*

## The Services (assignment deliverable #1)

| # | Deployable unit | Kind | Owns (tables) | Serves |
|---|---|---|---|---|
| 1 | **api-gateway** | nginx proxy | — | single entry `:8080`, path-routes to services |
| 2 | **catalog-service** | Spring Boot | `catalog_items`, `inventory_stock` | `GET /items`, `GET /inventory/low-stock` |
| 3 | **checkout-service** | Spring Boot | `transactions`, `transaction_line_items` | `POST /transactions`, `/{id}/items`, `/{id}/complete`, `GET /transactions/{id}` |
| 4 | **analytics-service** | Spring Boot | `popular_items_snapshot` | `GET /analytics/popular-items` |

Supporting library (not a service): **shared-persistence** — the data-access coupling surface.

## Project Structure

### Documentation (this feature)

```text
specs/003-service-based-architecture/
├── spec.md
├── plan.md        # This file
└── research.md    # Service-based style, shared-DB access, Docker vs jar, gRPC, concurrency
```

### Source Code (repository root)

```text
service-based/
├── pom.xml                     # Aggregator (modules); parent = spring-boot-starter-parent 4.1.1
├── docker-compose.yml          # db + 3 services + gateway
├── data/init/                  # DB-owned schema + seed (run once at first init)
│   ├── 01-schema.sql           # all 6 tables; CHECK (stock >= 0)
│   ├── 02-seed-catalog.sql     # 2000 catalog_items (sku, name, price)
│   └── 03-seed-inventory.sql   # 2000 inventory_stock (sku, stock=10000)
├── shared-persistence/         # Thin library: the DB-access coupling surface ONLY
│   └── .../shared/
│       ├── catalog/            # CatalogItem + CatalogItemRepository   (read by 3)
│       ├── inventory/          # InventoryStock + InventoryStockRepository.decrementStock
│       └── scan/               # ScanEvent + ScanEventRepository (write Checkout, read Analytics)
├── catalog-service/            # product reference data (catalog + inventory)
│   ├── Dockerfile
│   └── .../catalog/{CatalogApplication, CatalogController, LowStockController}
├── checkout-service/           # transaction lifecycle + stock decrement + scan writes
│   ├── Dockerfile
│   └── .../checkout/{CheckoutApplication, api/*, transactions/*, data/*}
├── analytics-service/          # hopping-window popular items over the shared scan log
│   ├── Dockerfile
│   └── .../analytics/{AnalyticsApplication, AnalyticsController, AnalyticsService, data/*}
├── api-gateway/                # nginx reverse proxy
│   ├── Dockerfile
│   └── nginx.conf
└── README.md
```

**Structure Decision**: Multi-module Maven reactor. Each service is its own module → its own fat jar → its own Docker image → its own container. `shared-persistence` is a plain library jar (no `@SpringBootApplication`, no repackage). Each service declares an explicit `@EntityScan`/`@EnableJpaRepositories` footprint naming exactly the data domains it is allowed to touch — ownership expressed in code, not just prose.

## Key Design Decisions

### 1. Shared data-access distribution — the flagged question
**Decision**: One physical database, **logically partitioned into per-service data domains**. A thin **`shared-persistence` jar** holds the JPA entities + repositories for **only the tables accessed by more than one service** — `catalog_items` (read by Catalog, Checkout, Analytics), `inventory_stock` (written by Checkout, read by Catalog), `scan_events` (written by Checkout, read by Analytics). **Single-owner tables stay private** to their service (`transactions`/`transaction_line_items` in checkout-service; `popular_items_snapshot` in analytics-service).

**Rationale**: Per *Software Architecture: The Hard Parts* (Ch. 9–10), the data-ownership direction is to keep each service's entities/repositories in that service and share as little schema as possible. A fat shared-domain jar re-couples everything (every schema change → lockstep redeploy). Restricting the shared jar to the genuine coupling surface makes the coupling **explicit, minimal, and reviewable** — it is literally the "database-access layer" where the assignment says coupling should live.

### 2. Which services communicate directly — none
**Decision**: **No domain service calls another.** Every cross-domain need is met through the shared DB: Checkout reads `catalog_items` for price/name, writes `scan_events` for Analytics, and decrements `inventory_stock`; Analytics reads `scan_events`; Catalog reads its own tables. The only HTTP hops are **gateway → service** (client-facing routing).

**Rationale**: This is the defining property of the style and the assignment's explicit instruction. Integration-through-data keeps the critical section (stock decrement) inside one local transaction (Decision 4).

### 3. Deployable unit — Docker containers, not bare jars
**Decision**: `docker-compose` with one container per service + one Postgres container; gateway in nginx.

**Rationale**: Bare jars on different ports simulate separate *processes*; containers simulate *independent deployability* — the property this style is actually about. One command stands the system up; a single service can be restarted or `--scale`d to show fault isolation and independent scaling. Real Postgres (not H2) so the concurrency semantics under load are honest.

### 4. Stock decrement — atomic conditional UPDATE at the DB layer
**Decision**: Checkout issues `UPDATE inventory_stock SET stock = stock - 1 WHERE sku = ? AND stock > 0` (via the shared `InventoryStockRepository.decrementStock`) **inside its own `@Transactional` completion**, backed by a DB `CHECK (stock >= 0)`.

**Rationale**: The check-and-decrement is one atomic statement, so even though the *decision* is in the Checkout service there is no cross-service race window; the row write-lock serializes concurrent decrements and only one matches the last unit. Because the inventory table shares the Checkout service's database, the decrement stays in the same local transaction that finalizes the receipt — the monolith's correctness is preserved verbatim. This is why Decision 2 (no service call) matters: a REST hop to an Inventory service would have pushed this across a network boundary and required compensation.

### 5. Inter-service protocol — REST at the gateway, never gRPC
**Decision**: The gateway→service hop is plain HTTP/REST. No gRPC anywhere.

**Rationale**: There are no inter-domain calls for gRPC to carry, and the one hop that exists (gateway→service) mirrors the synchronous REST client contract. gRPC's binary HTTP/2 + protobuf toolchain buys nothing at this scale and adds a second protocol.

### 6. Schema ownership & seeding — the database owns the schema
**Decision**: Schema + seed live in `data/init/*.sql`, executed once by the Postgres container at first init. All services run `ddl-auto: none`.

**Rationale**: With several services on one database, per-service `ddl-auto: create` would race and stomp each other's tables. Letting the database own the schema is the honest "single centralized database" model and makes each service a pure consumer of a shared schema (an *integration database* contract).

### 7. Analytics coupling — pull from the shared scan log
**Decision**: Checkout writes a `scan_events` row per scan; analytics-service runs a scheduled task that recomputes the hopping window (size 1000, slide 500) when a new slide boundary is crossed, persisting `popular_items_snapshot`, and serves the latest snapshot. `scan_events.id` is the monotonic global sequence (so `windowStart`/`windowEnd` are id values).

**Rationale**: Keeps analytics coupled to checkout only through the database, with no call in either direction. Deliberately simpler than the week-3 threaded pipeline — in this style the point is the *deployment* boundary, not the internal analytics mechanism.

## Phase Breakdown: Learn → Build → Improve

### Phase 1 — Learn
| Topic | Key concepts |
|---|---|
| Service-based style | coarse-grained domain services, shared DB, independent deployability (Richards Ch.13) |
| Data ownership | per-service data domains, shared vs private tables, integration database (Hard Parts Ch.9–10) |
| Multi-module Maven | aggregator POM, library jar vs Spring Boot fat jar, inter-module deps |
| Docker Compose | one container per service, a DB container, init scripts, healthchecks, `depends_on` |
| JPA across modules | `@EntityScan`/`@EnableJpaRepositories` to wire shared entities into each service |

### Phase 2 — Build (done in this scaffold)
1. Split the schema into data domains; move `stock` into `inventory_stock`; generate split seed SQL.
2. `shared-persistence` library: the three cross-boundary entities + repositories (incl. atomic decrement).
3. Three service modules, each with an explicit data-domain footprint, porting the business logic.
4. nginx gateway routing the 7 endpoints by path.
5. `docker-compose.yml`: db + 3 services + gateway.
6. `mvn package` → `docker compose up --build` → point the load client at `:8080`.

### Phase 3 — Improve / Validate
1. Run the load client with default params (10 stations, 60s) → save `quality-attribute-analysis/default.json`.
2. Run stress mode (`--stations=100 --duration=120`) → save `quality-attribute-analysis/stressMode.json`.
3. Verify the stock invariant (`initial - final == completed line items`, never negative) after each run.
4. Compare p95/p99 against the pipeline baseline; note the gateway hop and shared-DB contention as the new cost centers.

## Complexity Tracking

No constitution violations to track. The one accepted complexity is the `shared-persistence` coupling surface — justified in Decision 1 as the minimal, explicit database-access coupling the style requires.
