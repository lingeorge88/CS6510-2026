# Analytics Pipeline Research

**Date**: 2026-10-02  
**Method**: Repository inspection plus three delegated, read-only reviews: analytics design, checkout stock performance, and Spec Kit workflow. No server or load test was run for this research.

**Current decision**: The user selected database-first plan A with append-only snapshots. In-memory plan B is deferred.

> **Revision (2026-10-02):** The checkout coupling planned here (reserve-before-write admission, query barrier) was reverted after load testing: analytics is now non-blocking best-effort after basket writes (dropped on saturation) and GET reads the in-memory `AtomicReference`. The seed is 10,000, not 100,000. Load runs were captured by the user; see `pipeline/README.md` for shipped behavior and the +26% stress result. Note the historical-report table below cites 100,000 as the chosen seed — that was superseded.

## Repository findings

`layered/` uses Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, PostgreSQL, and ArchUnit. Its package dependencies are `api -> transactions/analytics -> data`. `TransactionService.scan()` calls `AnalyticsService.recordScan()` after saving a basket line and updated basket totals. `complete()` is already transactional; line-item lookup already has a transaction-ID index.

The current popularity implementation saves each scan, queries the latest 1000 database rows every 500 scans, deletes snapshot rows, then inserts ten ranked rows. This all happens on scan request threads.

| Finding | Consequence | Proposed resolution |
| --- | --- | --- |
| Sequence allocated before independently committed scan writes | Commit order may differ from sequence order | Single ingest worker assigns sequence in admitted FIFO order |
| Query selects latest rows without bounding them to the triggering sequence | Counted scans can disagree with reported window boundaries | Window filter owns exact ordered deque and counts |
| Multiple request threads can recompute simultaneously | Newer/older window publication can race | One ordered publisher |
| Delete and each snapshot save use separate transactions | GET can see empty/partial/mixed snapshots | One immutable snapshot publication |
| Only top ten ranks are persisted | `limit > 10` cannot return more items | Store full ranking in memory; slice on GET |
| Count ties and snapshot reads have no explicit ordering | Unstable ordering | Count descending, SKU ascending, return rank order |
| Analytics YAML values are unused constants | Configured policy has no effect | Bind validated configuration |
| Warmup returns `computedAt=""` | Violates the date-time schema | Use valid startup timestamp until first snapshot |
| Existing tests cover startup and dependency rules | Window behavior is unverified | Add focused analytics and lifecycle tests |

Low-stock is a current-inventory query, not a scan window; keep it outside the pipeline.

## Follow-up review: snapshot lifetime, structures, and transactions

The user requested further delegated research and raised the initial seed to 100,000 per SKU. The full findings and source links are in [backend-optimization-review.md](backend-optimization-review.md).

Snapshot deletion is unnecessary for selecting the latest generation. In plan B there are no snapshot SQL operations: replace one immutable reference and release old values as readers finish. If persistence is retained, append all ranks of a generation in one transaction, query by latest window with rank ordering, and prune periodically only if needed. Startup wiping permits finite-run history but does not bound a long-running process.

Use Java bounded blocking queues between filters. The selected database phase retains SQL aggregation over exact sequence bounds and appends complete snapshots transactionally. A worker-owned `ArrayDeque`/`HashMap` is reserved for a later in-memory comparison. Optional catalog metadata caching can remove repeated reads, but must exclude stock and keep the catalog array order stable for the client's Zipf workload. Small receipt collections are a lower-priority allocation improvement.

Keep completion's one commit. Shorten the period holding inventory locks by preparing independent values before updates and mapping copied results after a separate writer commits. Do not commit inventory/status separately. A short atomic scan writer is an optional consolidation of the existing separate commits; queue admission stays outside it. The current code also lacks a shared locking/version strategy for simultaneous operations on the same basket, which must be addressed if that additional hardening is selected.

## Three implementation alternatives

| Plan | Filters and pipes | Benefits | Costs and limits |
| --- | --- | --- | --- |
| **A: Preserve database analytics (selected)** | Ingest/persist -> exact-range SQL -> transactional append; bounded Java queues | Retains raw scan log and snapshot history; closest to current logic | Per-scan writes remain; serial persistence may bottleneck, and full-rank history increases snapshot writes |
| B: In-memory analytics (future comparison) | Ingest/sequence -> rolling window -> rank/publish; bounded Java queues | Small dependency-free pipeline; eliminates analytics SQL and catalog lookups during ranking; straightforward exact windows | No crash durability or replay; needs explicit admission, barriers, failure handling, shutdown |
| C: Durable analytics | Transactional outbox -> window/count -> persistent publication; queues between workers | Basket commit can durably record analytics input; supports replay | Additional schema, polling, checkpoints, deduplication, recovery, and transactional scan changes |

All three fit within a single Spring Boot process and leave client-facing endpoints synchronous. A broker is unnecessary for this assignment. Spring Integration queue channels could implement the pipes, but add dependency and configuration complexity without addressing commit ordering or window semantics automatically.

Plan A is the selected phased implementation. Keep current database analytics, remove per-hop deletion, fix exact bounds/publication, and let the user measure before selecting further optimizations. Plan B remains a plausible performance alternative rather than an implemented change. The API contract requires analytics responses, not raw scan persistence, and startup uses `ddl-auto: create`; neither fact alone establishes a measured performance advantage.

## Concurrency and persistence evidence

Java blocking queues provide bounded capacity, blocking/timed operations, and safe visibility across threads. They need an application-defined shutdown protocol. These support the proposed worker pipes; sequencing and barriers remain application responsibilities. [Java 21 BlockingQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

Publish an entire immutable snapshot through `AtomicReference`; a query barrier returns the snapshot captured when the publisher processes the barrier. [Java 21 AtomicReference](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html).

Spring transactions are thread-bound and do not transfer to worker threads. For plan A, use a separate proxied transactional writer or `TransactionTemplate`; publish cached output only after the persistence transaction commits. If atomic scanning is added, transaction-bound events support after-commit handoff, but without a transaction the listener is skipped by default. An after-commit callback alone is not a durable outbox. [Transactional annotation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html), [Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html).

## Stock and historical performance

The seed has 2000 SKUs at 10,000 units each. Existing reports provide historical context only:

| Report | Stations / requested seconds | Scans | Transactions/sec | SCAN p95 / p99 ms | COMPLETE p95 / p99 ms | SKUs at zero stock |
| --- | --- | --- | --- | --- | --- | --- |
| `layered/quality-attribute-analysis/defaultTest.json` | 10 / 60 | 339,107 | 536.85 | 1.81 / 3.06 | 5.66 / 8.59 | 4 |
| `layered/quality-attribute-analysis/stressMode.json` | 100 / 120 | 635,016 | 502.16 | 27.27 / 42.15 | 29.31 / 45.05 | 7 |

Both report zero HTTP errors, but depleted units are removed from completed baskets by the current fulfillment logic. These results cannot establish a healthy high-stock baseline. The client uses Zipf weights `1 / rank`; the hottest catalog rank receives approximately 12.23% of scans. At the saved default/stress volumes its expected demand is approximately 41,500 / 77,600 units. The user selected an initial seed of 100,000 per SKU. Use that value and reseed for each run; it provides headroom at the historical stress volume. Check actual depletion because higher pipeline throughput could increase demand, and revisit the seed based on evidence rather than claiming exhaustion has been eliminated.

Keep current stock handling initially: one completion transaction, atomic guarded decrement per unit, sorted SKU lock acquisition. Raising stock increases headroom but does not remove UPDATE locks. Removing the guard also does not eliminate row locking. PostgreSQL waits on concurrent updates and reevaluates the predicate against the updated row. [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html), [Explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html).

An optional optimization is one guarded quantity decrement per distinct SKU, in sorted order:

```sql
UPDATE catalog_items
SET stock = stock - :quantity
WHERE sku = :sku AND stock >= :quantity;
```

If zero rows are updated, use the current per-unit path to preserve partial fulfillment. The client distribution gives an estimated 10.5 units versus 9.34 distinct SKUs per basket: roughly 11% fewer stock UPDATE statements, not a predicted throughput improvement. Modifying queries can leave managed entities stale; do not indiscriminately clear pending transaction state. [Spring Data JPA modifying queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).

Profile stress-run waits with `pg_stat_activity`; use `pg_stat_statements` only if already available. Do not add database configuration work just for this proposal. Keep the pool at ten: the existing YAML records a previous experiment where a larger pool worsened tail latency without improving throughput. [PostgreSQL monitoring](https://www.postgresql.org/docs/current/monitoring-stats.html), [Statement statistics](https://www.postgresql.org/docs/current/pgstatstatements.html).

The completion comment claims receipt construction precedes stock locks, but current code builds totals and receipt after `releaseStock()`. Locks last until transaction commit. Sorted SKU acquisition prevents that particular inventory deadlock pattern, not every possible application deadlock.

## Spec Kit fit

Local infrastructure is already present: version metadata says 1.0.6, Claude integration is installed, the constitution is an untouched template, and the feature pointer still targets the original monolith. `.specify/` and `.claude/` are ignored by Git. The old monolith plan describes a different architecture and Spring version; preserve it as history.

These draft feature documents follow the local spec/plan/tasks organization without invoking workflow commands or changing integration state. Adopt the existing-project workflow for this bounded refactor: specify -> clarify -> plan -> tasks -> analyze, then implement and verify. [Spec Kit existing-project guide](https://github.github.com/spec-kit/guides/existing-projects.html), [Agentic SDD reference](https://github.github.com/spec-kit/reference/agentic-sdd.html).

If command integration is useful, install Codex alongside Claude with `specify integration install codex`; optionally select it with `specify integration use codex`. Codex uses `.agents/skills/` and `$speckit-<command>` invocations. This is an optional tooling change, not needed to implement the Java pipeline. Do not force-reinitialize the repository. [Official integration reference](https://github.github.com/spec-kit/reference/integrations.html).

Record the feature guardrails in its spec: unchanged API/client, analytics-only scope, bounded queues, exact windows, atomic publication, targeted verification, and real report evidence. The placeholder constitution imposes no implemented gates; no global constitution change is needed for this planning deliverable.
