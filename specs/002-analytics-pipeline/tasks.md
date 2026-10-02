# Tasks: Database Analytics Pipeline

The user selected database analytics and canceled automatic baseline/load capture. Later in-memory migration and other optimizations depend on user-run measurements. See [plan.md](plan.md).

## Phase 0: Initialization

- [x] T001 Copy layered source/build into `pipeline/`, preserving original project and shared client/spec.
- [x] T002 Set new Maven identity, seed 100,000 units per SKU, retain shared `selfcheckout` production database/pool/startup reset.
- [x] T003 Add Maven baseline profile compiling original Java sources to separate output.
- [x] T004 Verify both builds and ensure default tests use isolated H2.

## Phase 1: Database pipeline

- [x] T005 Implement validated policy/queue/timeouts and immutable input/boundary/ranking/barrier messages.
- [x] T006 Implement ordered raw-scan persistence filter.
- [x] T007 Add exact-range aggregation with catalog join, all ranks and deterministic ties.
- [x] T008 Implement single-transaction append-only snapshot writer and committed-output publication.
- [x] T009 Add latest-window/rank index and explicit snapshot rank ordering.
- [x] T010 Integrate reserve-before-write/submit-after-write scan admission, preserving other business logic.
- [x] T011 Implement query barriers, failure propagation, backpressure and draining lifecycle.
- [x] T012 Verify boundaries, ranking above ten, exact counts, raw continuity and retained snapshot generations.
- [x] T013 Verify transactional rollback, reservation cancellation/saturation, concurrent producers, failed worker and shutdown.
- [x] T014 Preserve architecture checks and document implemented filters/semantics.

## Phase 2: User-owned measurements (do not run automatically)

- [ ] T015 User runs baseline default and 100-station/120-second load with fresh seeds.
- [ ] T016 User runs database pipeline default and stress with fresh seeds.
- [ ] T017 Preserve genuine JSON outputs, inspect endpoint metadata/history/stock, and compare throughput/p95/p99.
- [ ] T018 Add actual pipeline report links and final assignment description; publish when requested.

## Optional later changes after evidence

- [ ] T019 Evaluate deliberate ingest microbatching and snapshot persistence overhead.
- [ ] T020 Evaluate immutable metadata cache and controlled catalog ordering.
- [ ] T021 Evaluate quantity stock decrements separately.
- [ ] T022 Evaluate atomic scan writer and shortened completion mapping, preserving one completion commit.
- [ ] T023 If selected, harden scan/completion with compatible transaction-row locking/versioning and race tests.
- [ ] T024 Evaluate START entity detection and receipt allocations only if profiles justify them.
- [ ] T025 Compare in-memory analytics only after database results; retain bounded Java queue pipes if adopted.
