# Research: Service-Based Architecture Decisions

Decision record for the five design questions the refactor raised. Primary anchor: Richards & Ford.

## 1. Service-based vs microservices — and how many services

Service-based architecture (Richards & Ford, *Fundamentals of Software Architecture*, Ch. 13) is a distributed style: coarse-grained, independently deployed **domain services** over a **single shared database** — a pragmatic middle ground that keeps ACID writes and avoids database-per-service, sagas, and a service mesh. Guidance is **4–12 services, averaging ~7**, kept coarse precisely because they share a database.

**Decision**: Decompose by business function into coarse-grained domain services; **merge Catalog + Inventory**; land at **3 domain services** (small domain — over-decomposition is the wrong answer). Below the 4–12 band is acceptable and preferable to fine-grained slicing.

## 2. Distributing the shared data-access layer (the flagged question)

Options considered: (a) one fat shared persistence jar of all entities/repositories; (b) each service owns its slice of the schema, all pointing at one physical DB; (c) duplicated access code.

*Software Architecture: The Hard Parts* (Ch. 9–10, data ownership / data domains) favors **(b)**: keep each service's entities and repositories in that service; share as little schema as possible. A fat shared jar (a) re-couples everyone — a schema change forces a lockstep redeploy. Duplication (c) drifts.

**Decision**: Hybrid of (a)+(b). One physical DB, **logically partitioned into per-service data domains**. A **thin `shared-persistence` jar** carries only the tables accessed by more than one service (`catalog_items`, `inventory_stock`, `scan_events`); single-owner tables stay private to their service. This makes the DB-access coupling **explicit and minimal** — the "database-access layer" the assignment points at. Treat the shared schema as an integration-database contract (Fowler, *IntegrationDatabase*): changes to shared tables must be backward-compatible.

## 3. Deployable unit — Docker vs bare jar

Bare jars on different ports simulate separate **processes**; containers simulate **independent deployability**, which is the property the style is about. docker-compose gives one-command standup, per-service restart/scale, and resource isolation; a Postgres container is a more honest "single centralized database" than embedded H2, and its locking semantics under load are the ones that matter for the stock invariant.

**Decision**: **docker-compose** — one container per service + one Postgres container + an nginx gateway. H2 retained only for optional fast unit tests.

## 4. Inter-service communication — REST vs gRPC vs none

The assignment says avoid direct calls; coupling is at the DB layer. In this design **no domain service calls another** — all integration is through the shared database. The only hop is **gateway → service** (client-facing routing), kept as plain REST to mirror the synchronous client contract. gRPC (binary HTTP/2 + protobuf) pays off for heavy internal east-west traffic and strict contract evolution (per Microsoft's guidance) — **none of which applies here**, so it would add a toolchain for no benefit.

**Decision**: REST at the gateway; **no gRPC**; **no inter-domain calls**.

## 5. Shared-database concurrency — never oversell

With the check-then-decrement split across a service boundary, a naive read-modify-write races. Options: (A) atomic conditional `UPDATE ... WHERE stock > 0`; (B) pessimistic `SELECT ... FOR UPDATE`; (C) optimistic version column.

(A) collapses the check and the decrement into one database statement — no cross-boundary window, shortest lock hold, best under hot-row contention, no retry loop. (B) is for multi-step logic between read and write; (C) suffers retry storms on a hot stock row.

**Decision**: **(A) atomic conditional UPDATE**, backed by a DB `CHECK (stock >= 0)`. Because the inventory table shares the Checkout service's database, the decrement stays inside the same local transaction that finalizes the receipt — the monolith's correctness is preserved verbatim.

## Primary citations

- Richards & Ford, *Fundamentals of Software Architecture*, Ch. 13 "Service-Based Architecture Style" — O'Reilly.
- Ford, Richards, Sadalage, Dehghani, *Software Architecture: The Hard Parts*, Ch. 9 (data ownership) & Ch. 10 (distributed data access / data domains) — O'Reilly.
- Martin Fowler, *IntegrationDatabase* — martinfowler.com/bliki/IntegrationDatabase.html.
- CodeOpinion, *Shared database between services, maybe* — codeopinion.com.
- Microsoft (Esposito), *Using gRPC in a Microservice Architecture* — MS Learn.

*Note: O'Reilly chapter pages block automated fetch; book-content claims above are corroborated by secondary summaries and should be confirmed against a copy of the books if verbatim quotes are needed in a paper.*
