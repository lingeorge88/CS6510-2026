# Research: Self-Checkout Monolith

**Date**: 2026-09-12 | **Plan**: [plan.md](plan.md)

## R1: Spring Boot Version Selection

**Decision**: Spring Boot 3.3.x (latest stable in the 3.x line)
**Rationale**: Requires Java 17+ (we have 21). Full Jakarta EE namespace support. Active LTS. Spring Data JPA 3.x included.
**Alternatives considered**:
- Spring Boot 2.7.x: Still uses `javax.*` namespace, EOL. No reason to use.
- Spring Boot 3.4.x: May not be GA yet. Stick with proven 3.3.x.

## R2: Build Tool — Maven vs Gradle

**Decision**: Maven
**Rationale**: More beginner-friendly for a learning project. XML `pom.xml` is explicit and widely documented. Spring Initializr generates a working Maven project out of the box. Gradle's Kotlin DSL has a steeper learning curve.
**Alternatives considered**:
- Gradle (Kotlin DSL): More concise, faster builds. Better for experienced developers. Unnecessary complexity for week 1.

## R3: PostgreSQL Connection Pooling

**Decision**: HikariCP (Spring Boot default)
**Rationale**: Spring Boot auto-configures HikariCP. For 10 stations, default pool size (10) is fine. For 100 stations stress test, increase to `spring.datasource.hikari.maximum-pool-size=50` — each station doesn't need a dedicated connection since operations are short.
**Alternatives considered**:
- c3p0, DBCP2: Older, slower, no advantage. HikariCP is the industry standard and already bundled.

## R4: Atomic Stock Decrement Approaches

**Decision**: Single `UPDATE ... WHERE stock > 0` statement
**Rationale**: PostgreSQL's row-level locking during UPDATE guarantees atomicity. A single statement is both correct and performant — no `SELECT FOR UPDATE` round-trip needed. Check `rows affected == 1` to confirm the decrement succeeded.
**Alternatives considered**:
- `SELECT FOR UPDATE` + `UPDATE`: Two statements, requires explicit `@Transactional`. Works but adds latency.
- Optimistic locking (`@Version`): Requires retry loops under contention. More complex code, worse performance when many stations hit the same SKU.
- Application-level `synchronized`/`ReentrantLock`: Only works in a single JVM. Breaks when you move to microservices in later weeks. Wrong mental model for the course.

## R5: Transaction ID Strategy

**Decision**: Java `UUID.randomUUID().toString()` with `tx-` prefix
**Rationale**: Matches the mock server's `tx-` prefix pattern. UUID avoids DB sequence contention. Unique without coordination.
**Alternatives considered**:
- Database SERIAL/SEQUENCE: Simpler IDs but adds a DB round-trip and a contention point under high concurrency.
- ULID/KSUID: Time-sorted, nice properties, but adds a dependency. Overkill for this use case.

## R6: Hopping Window Implementation

**Decision**: Store every scan as a row in `scan_events` with a monotonic `global_seq`. On every scan, check if `global_seq % 500 == 0`. If so, query the last 1000 scan events, aggregate by SKU, and persist top-N to `popular_items_snapshot`. The API reads the latest snapshot.
**Rationale**: Clean separation: scan events are append-only, snapshots are the materialized view. The modulo check is cheap. The aggregation query runs every 500 scans — infrequent enough to not impact throughput.
**Alternatives considered**:
- Compute on every API request (mock server approach): Correct but wasteful. Doesn't match the spec's "recomputed every slideInterval scans" semantics.
- Background scheduled task (`@Scheduled`): Recomputes on a time interval, not a scan-count interval. Doesn't match spec.
- In-memory ring buffer + periodic flush: Faster but loses data on restart. Assignment requires DB persistence.

## R7: Data Seeding Strategy

**Decision**: `spring.jpa.hibernate.ddl-auto=create` + `data.sql`
**Rationale**: `ddl-auto=create` drops and recreates tables on every startup — clean slate for each test run. `data.sql` runs after schema creation and inserts the 2000 catalog items. Simple, no migration tool needed.
**Alternatives considered**:
- Flyway/Liquibase: Proper migration tools, but overkill for week 1 where we want a fresh DB each run.
- `CommandLineRunner` bean: Programmatic seeding in Java. More flexible but more code to write. `data.sql` is simpler.
- `schema.sql` + `data.sql`: Explicit schema control. Could be useful if `ddl-auto=create` generates suboptimal DDL. Keep as a fallback.

## R8: Error Response Format

**Decision**: `@ControllerAdvice` with a `GlobalExceptionHandler` that catches domain exceptions and maps them to `{ "error": "CODE", "message": "..." }` with the correct HTTP status.
**Rationale**: Centralizes error handling. Each service throws a domain-specific exception (e.g., `TransactionNotFoundException`), and the handler translates it. Clean separation.
**Phase 2 note**: In the barebones version, it's OK to catch exceptions in controllers and manually construct the error JSON. The `@ControllerAdvice` approach is a Phase 3 improvement.
