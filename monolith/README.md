# Self-Checkout System Architectural Characteristics Analysis
## Monolithic Server


## Table of Contents

1. [Architectural Characteristics & Requirements](#1-architectural-characteristics--requirements)
2. [Top 3 Prioritized Characteristics](#2-top-3-prioritized-characteristics)

---

## 1. Architectural Characteristics & Requirements

### Reliability / Robustness

A checkout system needs an accurate view of inventory and predictable behavior when something goes wrong. It must protect stock data during concurrent checkouts while handling invalid input and edge cases without crashing.

- Stock must never go negative for any SKU, even under concurrent load
- Inventory decrements must be atomic so that concurrent transactions cannot reduce stock below zero
- Empty baskets, missing transactions, and invalid items must return appropriate client errors rather than server errors
- The system must maintain a 0% request error rate during the 10-station and 100-station load tests


### Performance

Checkout is an interactive process, so customers and staff should not be left waiting after a scan or payment.

- START and SCAN must respond in under 1 second under normal load
- COMPLETE transaction must finish in under 2 - 3 seconds
- Retain at least 75% of the 10-station performance for the above operations when load increases to 100 stations

### Operational Visibility / Analytics

Store staff need visibility into inventory and customer activity without querying the database manually. The system provides operational endpoints for identifying low-stock products and understanding which items are scanned most often.

- Expose all items below a configurable stock threshold through `/inventory/low-stock`
- Track each scan so popular-item activity can be calculated from the most recent 1,000 scan events
- Recalculate the popular-item ranking every 500 scans and return the latest top 10 items through `/analytics/popular-items`
- Capture latency, throughput, and request error rates during load tests so performance changes can be compared over time

### Scalability / Maintainability

The application's code is divided into JPA entities, repository interfaces, and a REST controller. These boundaries separate database mapping, data access, and HTTP handling so the system can grow without putting every business logic into one class.

- Keep table mappings inside entity classes and database queries inside repository interfaces rather than embedding SQL in controller methods
- Give each persisted concept its own entity and repository so inventory, transactions, and analytics can evolve independently
- Preserve the external API contract when persistence logic is changed or moved behind a future service layer
- Allow a domain such as inventory or analytics to be extracted into a separate service without rewriting the rest of the application

---

## 2. Top 3 Prioritized Characteristics

### 1. Reliability / Robustness (Highest Priority)

The system prioritizes predictable checkout behavior and safe inventory updates under concurrent load. Stock decrements are handled by an atomic database query, while invalid transaction states and empty baskets return explicit client errors. The load tests also completed with a 0% request error rate at both 10 and 100 concurrent stations.

**Trade-off:** Each scanned item requires a separate database update when the transaction is completed. These additional database calls increase completion latency, but the guarded update prevents the stored stock value from falling below zero. PostgreSQL is also the single source of truth, which improves consistency but makes the database a single point of failure.

### 2. Performance

Every scan and payment has a person waiting for feedback. Slow responses create a frustrating checkout experience and reduce the number of customers each station can serve.

**Trade-off:** Popular-item analytics are recalculated every 500 scans rather than after every scan. This reduces the amount of analytics work performed in the checkout path, but every 500th scan handles the recomputation and the analytics view may be up to 500 scans behind.

### 3. Scalability / Maintainability

The Spring Boot entity-repository-controller pattern gives the monolith clear internal boundaries. Entities define the persistence model, repositories contain built-in and custom database operations, and the controller owns the HTTP workflow. This is more structured than placing SQL, business rules, and request handling in one class. It also gives the project the ability and flexibility for adding a service layer or later extracting inventory and analytics into independent services without replacing the entire codebase.

**Trade-off:** This structure requires more classes, interfaces, configuration, and up-front development time than a bare-minimum monolithic server with database queries written directly inside controller methods. It also does not provide distributed scaling by itself: the application still runs as one deployable unit, and much of the business workflow is currently concentrated in one controller. The additional structure is worthwhile because it keeps persistence concerns separate and supports gradual refactoring into service classes, a layered architecture, or microservices as the system grows.

---
