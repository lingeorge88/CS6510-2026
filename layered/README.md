# Week 2 — Layered Architecture

Same API contract as Week 1 (`spec/self-checkout-openapi.yaml`), same Spring Boot 4.1.1 /
Java 21 / PostgreSQL stack, same `data.sql`. The only thing that changed is the internal
structure.

## Layers

```
api          CheckoutController, ApiExceptionHandler      HTTP only, no business logic
  ↓
transactions TransactionService, CheckoutException        basket lifecycle, catalog reads
  ↓
analytics    AnalyticsService                             scan ingestion, popular items, low stock
  ↓
data         5 entities + 5 Spring Data repositories      persistence only
```

Allowed dependency edges:

| From | May depend on |
|---|---|
| `api` | `transactions`, `analytics`, `data` |
| `transactions` | `analytics`, `data` |
| `analytics` | `data` |
| `data` | nothing in this project |

These are enforced as a **build failure** by
`src/test/java/com/selfcheckout/layered/architecture/LayeringRulesTest.java` (ArchUnit,
4 rules). Verified non-vacuous: planting a repository reference in the controller fails
the build.

## What moved where

The monolith's 301-line `CheckoutController` held all seven endpoints, all business logic,
and the analytics recompute. Every method in `TransactionService` and `AnalyticsService`
carries a Javadoc line reference back to the monolith source it came from.

| Monolith | Now |
|---|---|
| `getAllItems` L42-46 | `TransactionService.listCatalog` |
| `startTransaction` L56-77 | `TransactionService.start` (+ `@Valid` replaces the manual null check) |
| `addItem` L89-127 | `TransactionService.scan` |
| `addItem` L110-116 | `AnalyticsService.recordScan` |
| `completeTransaction` L139-186 | `TransactionService.complete` + `releaseStock` + `buildReceiptLines` |
| `getTransaction` L196-208 | `TransactionService.find` |
| `getLowStock` L218-238 | `AnalyticsService.lowStock` |
| `getPopularItems` L250-273 | `AnalyticsService.popularItems` |
| `recomputePopularItems` L284-300 | `AnalyticsService.recompute` |
| 5 `@Autowired` fields | constructor injection, split across two services |
| 6 inline `ResponseStatusException` | `CheckoutException` + `ApiExceptionHandler` |

## Deliberate trade-offs

- **No DTOs.** Services return entities and `Map` responses, exactly as the monolith did.
  Cheaper to read and to track; the cost is that `api` is allowed to reference `data`, so
  "entities never escape the data layer" is *not* an enforceable rule here. Adding DTOs
  later would let that rule drop back to `("Transactions", "Analytics")`.
- **`transactions` calls `analytics` directly.** A relaxed (open) layered arrangement
  rather than routing through an event. Fewer moving parts; the alternative
  (`ApplicationEventPublisher`) would decouple the two for the event-driven week.
- **Two `@JsonIgnore` annotations** keep `CatalogItem.stock` and `Transaction.completedAt`
  out of responses the contract does not include. `stock` in `/items` was a Week 1 spec
  deviation — the OpenAPI `CatalogItem` schema and the reference mock server both emit
  only `sku`/`name`/`price`.

## Phase 1 is behaviour-preserving by design

Every known defect from Week 1 is carried forward verbatim: the per-unit decrement loop,
the discarded `decrementStock` return value, the non-`@Transactional` complete path, the
status flip before decrement, the N+1 in receipt grouping, three auto-commits per scan,
and the synchronous analytics recompute. The only `@Transactional` in the project is still
the one on `CatalogItemRepository.decrementStock`.

This is so the load test measures the cost of layering and nothing else. Optimisation is
Phase 2, measured separately.

## Results

Same machine, same load client, same contract.

| 10 stations / 60s | monolith | layered (final) |
|---|---|---|
| **tx/sec** | 368.34 | **521.8** (+42%) |
| START p95 / p99 (ms) | 0.76 / 1.41 | 0.86 / 1.34 |
| SCAN p95 / p99 (ms) | 1.65 / 2.89 | 1.89 / 2.76 |
| COMPLETE p95 / p99 (ms) | 23.21 / 28.18 | **6.28 / 9.15** |
| errors | 0 | 0 |

| 100 stations / 120s | monolith | layered |
|---|---|---|
| **tx/sec** | 313.38 | **533.8** (+70%) |
| COMPLETE p50 / p95 (ms) | 41.53 / 67.58 | **16.40 / 27.40** |
| errors | 0 | 0 |

Two things worth noting. First, **the layering itself cost nothing** — the pure refactor
measured 364.1 tx/s against the monolith's 368.34, inside run-to-run noise. Every gain
above came from the Phase 2 changes, not from the structure.

Second, **the monolith lost throughput under load** (368 → 313 going from 10 to 100
stations) whereas this holds flat (522 → 534). The saturation ceiling moved up and
stopped collapsing. It is still a ceiling, though: reaching 530 at 10 stations and the
same at 100 means the system saturates by 10 concurrent requests — the HikariCP pool
default of 10 connections is the next binding constraint.

### Correctness

The stock invariant (`initial - final == completed line items`, never negative) **passes**:

```
 skus_violating | units_never_decremented | negative_stock | verdict
              0 |                       0 |              0 | PASS
```

The monolith fails it — 64,895 units sold without a decrement at a reported 0.00% error
rate, because the load client only checks HTTP status and never validates the receipt.

## Phase 2 changes

| change | effect |
|---|---|
| Index on `transaction_line_items(transaction_id)` | **+20%** tx/sec; COMPLETE p50 14.83 → 5.61 ms; `seq_tup_read` 2.5 B → 0 |
| `complete()` in one `@Transactional` | ~12 auto-commits → 1, and the operation is finally atomic |
| Stock accounting: units that cannot be decremented are not sold | invariant passes; costs ~1.6% |
| Receipt built from one `findAllById` | removes the N+1 over distinct SKUs |

Stock decrements are issued **last**, immediately before commit, and in sorted SKU order.
Both matter: row locks are now held until commit rather than for their own mini-transaction,
so a long lock window on a hot SKU would cost more than the batching saves, and multiple
row locks in one transaction can deadlock without a consistent global ordering.

### Out-of-stock handling

The contract defines no out-of-stock error — `/complete` allows only 200, 404 and 409
(already completed / empty basket), and scans are explicitly not gated on stock. When a
SKU runs out, units that cannot be decremented are removed from the transaction rather
than sold, so the receipt reflects what was actually dispensed and the ledger balances.
This keeps stock non-negative *and* the invariant exact, without inventing a status code.

## Run it

```bash
createdb selfcheckout          # once; shared with monolith/, they cannot run concurrently
./mvnw spring-boot:run         # :8080, ddl-auto=create reseeds 2000 SKUs each boot
./mvnw test                    # includes the ArchUnit layering rules
```
