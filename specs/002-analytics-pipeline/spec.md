# Feature Specification: Database Analytics Pipeline

**Created**: 2026-10-02  
**Status**: Database pipeline implemented; default and stress load runs captured at the 10,000 seed (see `pipeline/quality-attribute-analysis/`).  
**Baseline**: Root README, original `layered/`, and `spec/self-checkout-openapi.yaml`.

> **Revision (2026-10-02):** The analytics-to-checkout coupling was reverted after load testing. Scans are handed to the pipeline **non-blocking and best-effort** after the basket writes commit (a saturated pipe drops the sample instead of blocking or failing the scan), and `GET /analytics/popular-items` reads the latest published snapshot from an in-memory `AtomicReference` rather than through an ordered query barrier. The original reserve-before-write admission and query-barrier design (described below and in `plan.md`/`research.md`) is retained only as history and in the lifecycle tests. The seed was also lowered from 100,000 back to 10,000 to match the baseline for an apples-to-apples comparison.

## Scope and selected design

Create `pipeline/` as a separate Maven project based on layered source. Retain database analytics: raw scans remain in PostgreSQL, SQL computes windows, and the output appends snapshot history without deleting previous generations. Use three filters connected by bounded Java blocking queues. The user selected this database-first phase before deciding whether to transition to in-memory analytics.

Preserve `layered/`, all seven HTTP routes, the OpenAPI contract, load-client code, and non-analytics business behavior. Initial stock is 100,000 per SKU. Reuse the existing `selfcheckout` database with startup table recreation/reseeding. Automated tests use isolated H2.

Do not run baselines or load tests: the user will run them later. Provide a reproducible Maven baseline profile that compiles original layered Java sources with the new project's seed/resources.

## User scenarios and acceptance

### P1: Exact window popularity

Successful scans count even when their basket is never completed. For default policy, snapshots cover `[1,1000]`, `[501,1500]`, etc.

1. At 999 accepted scans, GET returns no ranks, zero boundaries, and a valid initialization timestamp.
2. At 1000 it returns `[1,1000]`; at 1499 it still returns that snapshot.
3. At 1500 it returns `[501,1500]`; at 1749 it does not force another computation.
4. Equal counts sort by ascending SKU and use consecutive ranks.
5. Requests above limit ten can return all available ranks up to the requested limit.

### P1: Database history and complete publication

Every raw scan is persisted in pipeline sequence order before its window boundary is aggregated. Every window's ranked rows append in one database transaction. Older generations remain queryable; no partially written generation is published.

### P1: Admission and synchronous queries

**As shipped (see Revision):** record analytics best-effort and non-blocking *after* basket writes succeed; a saturated pipe drops the sample so a scan is never blocked or failed by analytics. A scan response acknowledges the committed basket write, not completed asynchronous analytics persistence. GET returns the latest published snapshot from the in-memory reference without waiting on a barrier. A failed worker fails any pending barrier waits and stops new admission.

*Original design (history):* reserve analytics capacity before basket writes, submit only after successful basket writes, release unused reservations on failure, and have GET wait for an ordered barrier and return the snapshot captured there.

### P2: Reproducible manual comparisons

Build original logic with `./mvnw -Pbaseline package`; build pipeline with the default Maven configuration. The user runs the unchanged client with defaults and `java -cp out Main --stations=100 --duration=120`, restarting between runs. Do not claim improved performance or create report JSON without actual runs.

## Functional requirements

- FR-001: Preserve HTTP routes, schemas, and synchronous request/response behavior.
- FR-002: Keep low-stock, catalog, basket lifecycle, and checkout layered.
- FR-003: Own global analytics sequencing in one ingest worker; order inputs after successful basket writes.
- FR-004: Retain configured defaults 1000 scans, 500 slide, ten returned items, and stock threshold 50.
- FR-005: Persist each raw scan before forwarding its eligible window boundary.
- FR-006: Query the exact inclusive `[end - windowSize + 1, end]` interval, including every ranked SKU and deterministic ties.
- FR-007: Append complete snapshot generations transactionally; never delete raw scans or old snapshots during analytics processing.
- FR-008: Publish cached output to the in-memory latest-snapshot reference only after the snapshot commits; GET reads that reference directly in rank order. (Original: latest database queries return rank order.)
- FR-009: Apply requested limit at response time; retain full database rankings.
- FR-010: Bound the pipes; hand scans off non-blocking and drop on saturation rather than blocking the request; serve GET without waiting; propagate worker failure; and drain on graceful shutdown. (Original: bound queues/admission/query waits.)
- FR-011: Transfer immutable messages, not managed entities or request persistence contexts.
- FR-012: Seed 2000 SKUs at 100,000 stock, reuse `selfcheckout`, and preserve startup recreation.
- FR-013: Keep existing stock decrement and completion transaction logic for this phase.
- FR-014: Compile original layered sources through a separate baseline Maven output directory.
- FR-015: User-owned load runs will supply genuine report JSON; no baseline captures are performed by the implementation agent.

## Success criteria and limits

Exact sequence ranges, ranking, retained history, atomic publication, limits above ten, queue admission, barriers, failure, and shutdown have targeted tests. Preserve the existing four architecture rules. Default tests must not write to production PostgreSQL.

Asynchronous input queues are volatile; preserving database analytics does not make queued-but-unsaved inputs crash durable. Basket scan writes still commit separately. Existing same-basket concurrent mutation limitations remain, and any hardening is a separate change.

Append-only history grows until restart. Full rankings can produce more per-hop inserts than the prior top-ten implementation. Performance is an open measurement question, not a success claim. See [plan.md](plan.md), [research.md](research.md), and [backend-optimization-review.md](backend-optimization-review.md).
