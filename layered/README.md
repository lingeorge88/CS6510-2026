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

## Results (10 stations, 60s, same machine)

| | monolith | layered | delta |
|---|---|---|---|
| tx/sec | 368.34 | 364.1 | −1.2% |
| START p95 / p99 (ms) | 0.76 / 1.41 | 0.75 / 1.23 | within noise |
| SCAN p95 / p99 (ms) | 1.65 / 2.89 | 1.65 / 2.55 | within noise |
| COMPLETE p95 / p99 (ms) | 23.21 / 28.18 | 23.16 / 25.50 | within noise |
| errors | 0 | 0 | — |

**Layering costs nothing measurable.**

Stock invariant (`initial − final == completed line items`) **fails identically to the
monolith**: 2 SKUs, ~23k units never decremented, no negative stock. That is the correct
Phase 1 outcome — the defect was preserved, not accidentally fixed.

## Run it

```bash
createdb selfcheckout          # once; shared with monolith/, they cannot run concurrently
./mvnw spring-boot:run         # :8080, ddl-auto=create reseeds 2000 SKUs each boot
./mvnw test                    # includes the ArchUnit layering rules
```
