# Speaker Notes — Layered Architecture Walkthrough

Short talking points, not a re-read of the slides. ~10–15 min. Deep dives go bottom-up (entity → repository → service → analytics → controller); slide 2 traces the request top-down, then we build it back up.

---

## Slide 1 — Title
Week 2 of the project. Same API contract and same load client every week — only the architecture changes, so any difference we see is the architecture, not the test. Route: shape → boot → a class per layer → analytics → performance → trade-offs.

---

## Slide 2 — Architecture: how a checkout flows
One request goes down through the layers and back. The controller takes the HTTP call and hands it to a service; the service runs the logic and, for data, goes through the data layer; the data layer is the only code that talks to PostgreSQL.

A scan travels controller → `TransactionService.scan()` → repositories → DB, and on the way the service also pings analytics to record the scan (the one red, sideways arrow).

Every arrow points downward — each layer knows only the *interface* of the one below. Right side: the 7-endpoint contract, reads in blue, writes in amber. Stack: Spring Boot, Java 21, Spring Data JPA (on Hibernate), PostgreSQL, HikariCP pool.

---

## Slide 3 — Bootstrapping
`./mvnw spring-boot:run` does a lot we never wrote. Maven compiles and launches with an embedded Tomcat (no separate server). Spring's IoC container builds our objects — controller, services, repositories — and wires them together.

Because `ddl-auto: create`, Hibernate reads the `@Entity` classes and generates the `CREATE TABLE` statements itself; then `data.sql` seeds 2000 products (a generated schema is empty). Each boot drops and rebuilds, so every benchmark starts identical.

**Why an ORM instead of hand-writing DDL/SQL:** the schema is derived from the classes, so it can't drift from the code; we skip a pile of error-prone boilerplate SQL; changing a field changes the schema automatically; and the same code ports across databases. Trade-off: `create` is for benchmarks — production uses migrations so real data is never dropped.

---

## Slide 4 — Data Layer: Entity
An entity is a Java class mapped one-to-one to a table — one object is one row, one field one column — and Hibernate moves data between them.

In `CatalogItem`: `@Id` makes `sku` the primary key (a natural key we assign, not generated); `price` is a `BigDecimal` because money must be exact (no floating-point `double`); `@JsonIgnore` keeps `stock` out of the `/items` response (it's exposed via `/inventory/low-stock` instead).

---

## Slide 5 — Data Layer: Repository
A repository is an interface for reading and writing one entity. We don't implement it — Spring Data generates the implementation at startup, so there's no `RepositoryImpl` class.

Two method styles: a **derived query** (`findByStockLessThan` — Spring builds the SQL from the method name, no body) and a **custom `@Query`** (`decrementStock`, JPQL, for what the name can't express).

The `stock > 0` guard is the key bit: `UPDATE ... WHERE sku = ? AND stock > 0` is checked atomically by the DB, so if two stations race for the last unit only one UPDATE matches and stock never goes negative. The `int` return is rows changed — 1 = got it, 0 = out — so we detect out-of-stock with no extra read and no lock. Pattern: Repository + Dependency Injection (Spring hands us a proxy).

---

## Slide 6 — Transactions Layer: Service
Where the business logic lives. `complete()`: validate (exists, open, not empty) → group units by SKU in a sorted `TreeMap` (same lock order everywhere ⇒ no deadlock) → decrement each via the guarded UPDATE → drop any unit that was out of stock → mark completed and return the receipt. The whole method is `@Transactional`, so it's one commit.

The invariant: `starting stock − ending stock == units sold`, stock never negative — dropping the un-decrementable units is what keeps it true. The receipt uses one `findAllById` instead of a lookup per SKU, avoiding an N+1.

---

## Slide 7 — Analytics Layer: Popular items
Each scan gets a `seq` — a sequence number, a running counter. It's *monotonic* (only ever increases, never repeats), generated with an `AtomicLong` (a counter safe to bump from many threads). Every 500 scans (once we have 1000) we recompute the top-10 and store it, so reads are cheap lookups.

Low-stock lives in the same class: `lowStock()` calls `findByStockLessThan(threshold)` (default 50), computed live on read.

The window is a *hopping window*: the newest 1000 scans, advancing in steps of 500, so consecutive windows overlap. In-memory counter ⇒ single instance (fine here, the scan table resets on boot).

---

## Slide 8 — Analytics: tracing the algorithm
Recompute: take the newest 1000 scans, group by SKU, count, sort, keep the top 10, and replace the stored snapshot. "Wipe the snapshot table" = delete the old window's rows before inserting the new top-10, so the table only ever holds the current ranking.

What "snapshot precompute" means: there are two ways to answer "what's the top 10?" — compute it on every read (scan the last 1000 events, group, count, sort; accurate, but you redo that work on every request), or compute it occasionally and store the finished answer. We do the second. *Precompute* = work out the answer ahead of time, before anyone asks; *snapshot* = the saved top-10 as of the last recompute. So the heavy work runs on writes (once every 500 scans, inside `recompute()`), and a read just fetches the stored rows via `findLatestSnapshot()` — no counting on the read path.

It's native SQL because JPQL can't put a `LIMIT` inside a subquery. Reads are **O(k)** — cost scales with the ~10 rows returned, not the whole scan history — because they just read the snapshot. It's **stale up to 500 scans** since we only rebuild every 500. That's the trade: cheap reads, slightly behind. Pattern: a materialized-view / cache.

The JSON on the slide is the actual `GET /analytics/popular-items` response: it echoes the window itself — `windowSize`, `slideInterval`, `windowStart`/`windowEnd`, `computedAt` — so the client can see exactly which window it's looking at, plus the ranked `items` (rank, sku, name, scanCount). The long tail (129, 55, 35…) comes from the client's weighted sampler.

---

## Slide 9 — API Layer: Controller
The controller is the thin HTTP door: it maps URLs to methods, validates the body, and delegates to a service — no business logic, no DB access. Annotations: `@RestController` (JSON), `@PostMapping` (verb + path), `@PathVariable` (id), `@Valid` (validation). The request `record`s are request DTOs.

**IoC:** instead of the controller calling `new`, Spring's container builds the services and passes them into the constructor; we just store them in `final` fields. That gives loose coupling, easy mocking in tests, and shared singletons.

**Errors:** services throw `CheckoutException(Kind)`; one `@RestControllerAdvice` maps it to 404/409, and an invalid body → 400. The `CheckoutException.transactionNotFound(...)` builders are the Factory Method pattern.

**DTOs:** we return entities directly, so the JSON is tied to the table schema (rename a column, the API changes). Response DTOs would decouple the two — we have request DTOs, not response ones.

---

## Slide 10 — Performance
At 10 stations: ~537 tx/s, zero errors, complete p50 2.7 ms. At 100 stations: ~502 tx/s, still zero errors, latency up (saturated). p50/p95/p99 are percentiles — we read the tail, not the average.

Biggest win: an index on `transaction_line_items(transaction_id)` → ~+20% throughput, complete p50 ~15 → 5.6 ms. `complete()` looks up line items on every checkout; no index means a full table scan (O(n)), the index makes it a direct jump. Numbers vary ~18% run to run, so trust the percentiles.

---

## Slide 11 — Trade-off analysis
Architectural: layers (clear seams vs more structure); entities as responses (less code vs wire tied to schema); DB for all state (durable, one source of truth vs a round-trip per op). Layering itself was ~free (364 vs 368 tx/s).

Business logic — the stock invariant is the one to dwell on.

*What "guarded atomic UPDATE" means:* the decrement is `UPDATE catalog_items SET stock = stock - 1 WHERE sku = ? AND stock > 0`. **Atomic** = the database does the check and the subtraction as one indivisible step, so nothing can slip in between. **Guarded** = the `AND stock > 0` only lets the update happen when stock is still positive. If two stations race for the last unit, only one UPDATE finds the guard true and succeeds; the other changes zero rows. No separate read, no explicit lock.

*How we handled the invariant:* the invariant is `starting stock − ending stock == units actually sold`, with stock never negative. In `complete()` we call that guarded UPDATE once per unit and look at its rows-affected — 1 means the unit was really decremented, 0 means the SKU was already out. We collect every 0-result unit (the ones the store couldn't actually dispense) and delete them from the transaction in one batch (`deleteAllInBatch`) *before* we total anything — so those units never make it onto the receipt, aren't counted in `itemCount`, and are never charged. What remains on the completed transaction is only units whose stock we genuinely decremented. That's exactly what makes `starting − ending == sold` hold, and because the guard refuses to decrement at zero, stock can never go negative. Cost is about 1.6% throughput. Honest caveat: the shopper already has the item in hand, so silently dropping it keeps our ledger clean but isn't realistic — a real store would need reconciliation (substitute, override, refuse at the till). The spec says keep an accurate count, not gate the scan, so dropping is the right call for this exercise.

*What "O(k) reads" means:* when a client asks for popular items, we just return the ~10 rows already stored in the snapshot — so the read cost scales with k, the number of items returned (~10), not with n, the total number of scans ever. Computing it fresh on each read would be O(n) and get slower as the store runs. That cheapness is the payoff of precomputing; the price is the list can be up to 500 scans behind, because we only rebuild the snapshot every 500 scans. (The 500/1000 numbers set freshness; the snapshot sets the read cost — two different things.)

We also verified the implementation meets the OpenAPI contract endpoint by endpoint.

---

## Slide 12 — Challenges & next steps
Challenges: (1) finding the bottleneck — it was a missing index, not CPU or the pool; took profiling. (2) deciding which invariants to enforce, since correctness costs throughput. (3) the real story — inspecting Week 1's code, out-of-stock items were over-sold: the guard blocked negative stock (UPDATE matched 0 rows) but the unit still counted as sold, so `start − end ≠ sold`. Fix: check rows-affected and drop the unit. It works, but isn't perfect (shopper has the item).

Next steps: find the real DB ceiling (throughput flattens ~537 tx/s); note that raising the connection pool did **not** help — the limit is elsewhere; add response DTOs; move `recordScan` off the scan path (async).

---

## Slide 13 — Q&A
Through-line: api → transactions → analytics → data → PostgreSQL, measured under load, correct on the last unit. Be ready for: DTOs (requests yes, responses no); IoC (loose coupling, testability); dropping out-of-stock units (satisfies the invariant, not realistic); concurrency (guarded UPDATE + sorted lock order); pool didn't help (bottleneck elsewhere); meets the contract (verified).
