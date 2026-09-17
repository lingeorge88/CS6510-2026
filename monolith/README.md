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

### Scalability

A supermarket may run 10 stations during quiet periods and more than 50 at peak times. As demand grows, the system should continue to behave correctly without a steep drop in performance.

- Support 100 concurrent stations with 0% error rate
- Retain at least 75% of the 10-station throughput when load increases to 100 stations

### Maintainability

Checkout rules will change over time as the store adds products, payment behavior, or reporting needs. The code should make these changes straightforward without affecting unrelated parts of the system.

- Checkout operations must use consistent request validation, HTTP status codes, and error responses
- Inventory updates and other critical business rules must be clearly named and documented
- Changes to one checkout operation should not require changes to unrelated operations

---

## 2. Top 3 Prioritized Characteristics

### 1. Reliability / Robustness (Highest Priority)

The system prioritizes predictable checkout behavior and safe inventory updates under concurrent load. Stock decrements are handled by an atomic database query, while invalid transaction states and empty baskets return explicit client errors. The load tests also completed with a 0% request error rate at both 10 and 100 concurrent stations.

**Trade-off:** Each scanned item requires a separate database update when the transaction is completed. These additional database calls increase completion latency, but the guarded update prevents the stored stock value from falling below zero. PostgreSQL is also the single source of truth, which improves consistency but makes the database a single point of failure.

### 2. Performance

Every scan and payment has a person waiting for feedback. Slow responses create a frustrating checkout experience and reduce the number of customers each station can serve.

**Trade-off:** Popular-item analytics are recalculated every 500 scans rather than after every scan. This reduces the amount of analytics work performed in the checkout path, but every 500th scan handles the recomputation and the analytics view may be up to 500 scans behind.

### 3. Scalability

Demand changes throughout the day, so the system must handle a large increase in active stations without introducing errors. Reliable behavior at 100 stations matters more than achieving the lowest possible latency at 10.

**Trade-off:** The current monolith is inexpensive and easy to deploy, but it has a lower scalability ceiling than a distributed design. Connection pooling, replicas, or separate services could support more traffic, but would add infrastructure and operational complexity.

---


