# Backend Data Structures and Transaction Review

**Date**: 2026-10-02  
**Status**: Research informing phased implementation; no load measurements performed.  
**Reviews**: Three delegated investigations covered snapshot lifetime and analytics structures, transactional backend structures, and transaction boundaries. Findings were checked against `layered/` and primary Java, Spring, Hibernate, and PostgreSQL documentation. Performance effects below are hypotheses until measured.

## Snapshot deletion

The user selected database analytics with append-only snapshots for the first implementation. The in-memory approach below remains an alternative to compare later; it is not the current pipeline.

`AnalyticsService.recompute()` calls `deleteAllInBatch()` for each hop, then saves each ranked row separately. The delete is unnecessary for selecting the latest result: the existing repository already filters by `MAX(windowEnd)`. However, simply removing the delete does not fix separately committed inserts; GET could still select a new but unfinished snapshot.

| Strategy | Correct publication | Retention and cost |
| --- | --- | --- |
| Current delete plus separate saves | Not atomic; readers can see empty/partial output | Repeated SQL and deletion work |
| Transactional delete-and-replace | One writer replaces all rows in one transaction | Bounded table, but ongoing delete/insert work |
| Append-only database snapshots | Insert every row of one snapshot in one transaction | Avoids deletion; retains history throughout the run |
| Append with periodic pruning | Same atomic append, prune only older complete windows | Bounds long-running history without per-hop deletion |
| In-memory publication, future plan B | Replace one immutable snapshot reference | No snapshot SQL; old snapshots become collectible after readers release them |

For future plan B, raw scan and snapshot database operations could be removed from the active analytics path. The selected plan A retains those operations and appends every complete generation in one transaction; a latest-result cache is updated only after commit.

For selected plan A, append-only snapshots are reasonable for finite benchmark runs because startup wipes the database. Retain every rank, add an index such as `(window_end, rank)`, and query with `ORDER BY rank`. Keep a single publisher and commit all rows together. Startup cleanup does not bound growth during a long uninterrupted run; periodic pruning remains an optional later decision, not part of the selected no-delete phase. Avoid replacing the delete with a per-hop `TRUNCATE` operation.

Deletes leave obsolete PostgreSQL row versions that vacuum later reclaims. Removing them eliminates real work. The current path normally deletes roughly ten rows every 500 scans, though; eliminating per-scan inserts and ranking-time catalog queries is likely more consequential. That relative-cost conclusion is an inference, not a measured result. [PostgreSQL vacuuming](https://www.postgresql.org/docs/current/routine-vacuuming.html), [Indexes and ordering](https://www.postgresql.org/docs/current/indexes-ordering.html).

## Analytics structures

The current database pipeline uses immutable boundary/frame messages, SQL grouping, and a committed-snapshot cache. The deque/count-map design below applies only to the future in-memory alternative.

The in-memory pipeline still uses **bounded Java `ArrayBlockingQueue` pipes**. They coordinate worker threads and provide FIFO transfer/backpressure. The deque and count map below belong exclusively to the window worker; they are not inter-thread pipes.

| Responsibility | Proposed structure | Reason |
| --- | --- | --- |
| Input and sequenced-scan transfer | `ArrayBlockingQueue` | Fixed capacity, FIFO, timed waiting; no per-message linked queue node |
| Window retention | `ArrayDeque<WindowScan>` | Append newest and evict oldest in amortized constant time; preallocate for window size plus one if appending before eviction |
| Per-SKU counts | Worker-owned `HashMap<String, CountAndName>` | Increment incoming SKU, decrement evicted SKU, remove zero counts |
| Hop handoff | Immutable copy of counts and bounds | Later scans cannot mutate an already emitted frame |
| Ranking | Sort a list by count descending, SKU ascending | At most 1000 distinct SKUs in a 1000-scan window; sort only every 500 scans |
| Current result | `AtomicReference<AnalyticsSnapshot>` | Publish metadata and all ranks as one complete value |
| Query consistency | Barrier message with `CompletableFuture<AnalyticsSnapshot>` | Capture the result after all preceding inputs; pass barriers even without a new frame |

`ArrayDeque` is not thread safe and does not impose a maximum size: the window worker must enforce eviction. Its single owner avoids synchronization. Hash operations are expected constant-time with suitable keys. Keep ranking complete rather than maintaining a fixed top-ten heap, since GET accepts limits above ten. A continually sorted tree adds per-scan work and complicates count decrements. Numeric SKU IDs, primitive counters, a custom ring buffer, or worker microbatching are later options only if CPU/allocation measurements justify them. A batch must still process every hop and barrier in order. [ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html), [HashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html), [ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html).

Carry the SKU/name already retrieved by scanning into the input, so the publisher does not query the catalog. A frame can contain up to 1000 entries, which is why its queue should be much smaller than the scan queues. Publish no mutable map views or JPA entities.

## Existing transactional backend structures

The basket is persisted as unit rows plus a transaction row with maintained count and total; it is not an in-memory shared basket map. Default baskets contain only 1–20 units, so database calls likely matter more than swapping small collections. The following are separate optimization candidates rather than requirements for the analytics refactor.

| Function | Current representation | Assessment and candidate |
| --- | --- | --- |
| Catalog listing | `List<CatalogItem>` from unordered `findAll()` | Optional immutable metadata list sorted by SKU, plus a separate lookup map |
| Start transaction | Assigned string UUID followed by repository `save()` | Profile new-entity detection and an avoidable merge existence check |
| Read transaction | Persisted `itemCount` and `BigDecimal runningTotal` | Keep; avoids recomputing totals from basket rows |
| Scan item | Transaction/catalog queries, unit insert, basket-total update | Optional metadata cache and one atomic basket-write transaction |
| Completion grouping | `TreeMap<SKU,List<TransactionLineItem>>` | Sorted keys serve inventory lock ordering; retain ordering explicitly |
| Completion catalog lookup | `LinkedHashMap<SKU,CatalogItem>` | Only key lookup is used; ordering adds little value here, but changing it is low priority |
| Fulfillment | Fulfilled/unfulfilled unit lists | Retain needed unit identities for existing partial-fulfillment deletion |
| Receipt construction | A second grouping into nested mutable maps | Optional typed per-SKU accumulator and immutable receipt result to avoid regrouping/casts |
| Inventory | Per-unit guarded UPDATEs | Optional one guarded quantity UPDATE per distinct SKU with per-unit fallback |
| Low-stock | Live database query and an alert list | Keep; only 2000 rows and one end-of-run request in this client |

A metadata cache can contain immutable `CatalogEntry(sku, name, BigDecimal price)` values for all 2000 SKUs. Populate it after seeding. Use a sorted immutable list for `/items` and an immutable map for lookup; cache no stock or managed entities. No catalog-edit endpoint exists today, but future edits would require invalidation. This can remove one catalog lookup per scan; the measured benefit is unknown.

Catalog response ordering is part of benchmark control: the load client treats returned array position as Zipf rank. A map's iteration order must not define that list. PostgreSQL does not guarantee row order without `ORDER BY`. If catalog ordering/caching changes, use the same explicit SKU order in the temporary layered baseline and pipeline runs. [PostgreSQL ordering](https://www.postgresql.org/docs/current/queries-order.html), [Java Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html).

Keep `TreeMap` or explicitly sort SKU keys before inventory updates. Its logarithmic operations are insignificant at at most twenty units compared with the likely SQL cost; this is a hypothesis. Use request-local lists/maps without concurrent containers. Keep monetary values in `BigDecimal`, and calculate receipts from prices captured at scan time. A typed SKU group must preserve actual unit-price differences if the same SKU was scanned at different prices. [Java TreeMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html).

Assigned UUIDs and no nullable version field mean Spring Data's default new-state detection chooses `merge` for transactions. Investigate observed START SQL before changing this; a lifecycle-managed `Persistable.isNew()` or explicit persist path is an option, not a proven saved query. [Spring Data entity persistence](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html).

Unit rows preserve captured prices and partial fulfillment. Aggregating them by transaction/SKU/price changes schema and concurrent writes for relatively modest savings under this workload; defer it. `IDENTITY` IDs also prevent normal Hibernate insert batching, so `saveAll()` or a batch-size property alone is not a batching solution. This matters only if database analytics or bulk basket persistence is chosen. [Hibernate batching](https://docs.hibernate.org/orm/7.1/userguide/html_single/#batch).

Keep the existing line-item transaction-ID index and SKU primary key. Do not add a stock B-tree index for a rarely queried 2000-row table without evidence: each decrement would maintain that index, and changing an indexed stock column prevents PostgreSQL's HOT optimization for that UPDATE. Inspect actual plans and update statistics first. [PostgreSQL HOT](https://www.postgresql.org/docs/current/storage-hot.html).

## Transaction boundaries

The only service-level transaction is `complete()`. There is no evidence yet that it is an excessively large transaction: default baskets have 1–20 units. Its duration and time holding hot inventory rows matter more than its source-code length.

`complete()` has one outer commit. The repository's default `REQUIRED` inventory updates join it; they do not independently commit each unit. By contrast, `scan()` has separate basket-line and basket-total repository commits, followed by the current analytics writes. [Spring Data transaction boundaries](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html).

### Shorten completion while preserving one commit

If profiling justifies the optional refactor, use a nontransactional facade calling a separate proxied writer or `TransactionTemplate`:

```text
Facade -> transactional writer:
  validate/lock basket and read its state
  group units and prepare price/receipt metadata
  decrement stock in sorted SKU order
  resolve fulfillment; save final totals/status
  return immutable CompletionResult
Commit -> facade formats response from copied values
```

Current code already groups units and fetches catalog metadata before stock updates, but calculates fulfilled totals and builds receipt maps afterward. Its comment claiming receipt construction precedes locks is inaccurate. Precompute what is independent of stock outcomes; keep final fulfillment and persisted totals inside the writer. Move pure response mapping after commit, using copied values with all required metadata validated before commit. JSON serialization is already outside the service transaction, so moving it is not a new improvement.

Do not read basket/status outside the writer without locking or version revalidation. Do not split stock decrements, basket cleanup, and completed status into separate commits or use `REQUIRES_NEW` per SKU. That creates partial purchases and additional commits. `flush()` does not commit or release transaction-held stock locks. A private annotated helper called by the same instance also does not establish a Spring proxy transaction boundary. [PostgreSQL locking](https://www.postgresql.org/docs/current/explicit-locking.html), [Spring transaction proxy rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

### Optional atomic scan writer

```text
Facade reserves analytics capacity outside transaction
  -> proxied scan writer:
       validate/lock basket
       insert unit and update totals in one transaction
       return immutable scan result
     commit
  -> facade transfers reserved input to pipeline
  -> synchronous HTTP response
```

This consolidates the existing two basket commits; it does not split completion. When a separately proxied writer returns, its own transaction has committed, provided the facade has no surrounding transaction. Release the reservation on rollback. No blocking queue wait, barrier, ranking, or shutdown join belongs inside a database write transaction. A volatile post-commit transfer still does not guarantee delivery across a process crash.

The minimal analytics plan continues to preserve separate scan writes. Choose the atomic writer explicitly as an additional, independently tested change.

### Existing same-basket races

`Transaction` has neither a version column nor a lock on reads. Two simultaneous completion requests can both read OPEN and decrement stock; a concurrent scan can overwrite basket state. The load client sends operations sequentially per station, so its successful reports do not establish correctness for these races. If hardening is selected, both scan and completion must acquire the same transaction-row lock before checking status, or use compatible versioned updates with conflict handling. `@Transactional` alone does not solve this. Keep transaction-row-before-sorted-inventory lock order consistent.

The `unfulfilledUnits` counter increments inside completion before commit; a rollback can leave that metric inflated. If that diagnostic is used, apply its increment only after a successful commit. Keep after-commit queue/metric work small and avoid database writes there unless they have a new explicit transaction. [Spring transaction synchronization](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html).

## Priority and measurement

1. Implement selected plan A with exact-range SQL, append-only transactions, bounded queues, committed publication, and the requested 100,000 seed.
2. Let the user run both required loads and verify final stock plus complete analytics metadata; do not capture baselines automatically.
3. Use the new Maven baseline profile to rebuild original layered Java sources with matched seed/pool/resources for user-owned comparisons.
4. Evaluate one additional change at a time: metadata cache, atomic scan writer, quantity decrements, shortened completion mapping, then START persistence detection.
5. Consider primitive arrays, schema aggregation, or additional indexes only if profiles show a need.

The user requested shared `selfcheckout` with startup reset; default automated tests use isolated H2. No new production database is required. Consider in-memory plan B only after the database-first results exist.

Track SQL call/commit counts, endpoint p95/p99 and throughput, inventory lock waits, and allocation/GC. If modifying transactions, test rollback after an intermediate decrement, duplicate completion, scan-versus-completion races, and exclusion of rolled-back scans from analytics. Use allocation profiling to distinguish collection overhead from persistence/HTTP work. No numeric improvement or bottleneck is established by this review.
