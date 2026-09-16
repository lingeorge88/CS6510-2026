# Quickstart: Self-Checkout Monolith

**Date**: 2026-09-12 | **Plan**: [plan.md](plan.md)

## Prerequisites

- **Java 21 JDK** (confirmed: `java -version` → 21.0.5)
- **Maven 3.9+** (`brew install maven` if not installed)
- **PostgreSQL 15+** (`brew install postgresql@15` or Docker)
- **Load client built**: `cd load-client && ./build.sh`

## Step 1: Set Up PostgreSQL

```bash
# If using Homebrew PostgreSQL:
brew services start postgresql@15

# Create the database:
createdb selfcheckout

# Verify:
psql selfcheckout -c "SELECT 1"
```

## Step 2: Generate Spring Boot Project

Go to https://start.spring.io/ with these settings:
- **Project**: Maven
- **Language**: Java
- **Spring Boot**: 3.3.x (latest stable)
- **Group**: com.selfcheckout
- **Artifact**: monolith
- **Packaging**: Jar
- **Java**: 21
- **Dependencies**: Spring Web, Spring Data JPA, PostgreSQL Driver

Download, unzip into `monolith/` at the repo root.

## Step 3: Configure Database

In `monolith/src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/selfcheckout
    username: ${USER}
    password: ""
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: false
  sql:
    init:
      mode: always
```

## Step 4: Build and Run the Monolith

```bash
cd monolith
./mvnw spring-boot:run
```

The server should start on port 8080. Verify with:
```bash
curl -s http://localhost:8080/items | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d[\"items\"])} items')"
# Expected: 2000 items
```

## Step 5: Validate with curl

```bash
# Start a transaction
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{"stationId":"station-001"}' | python3 -m json.tool

# Scan an item (replace TX_ID with the transactionId from above)
curl -s -X POST http://localhost:8080/transactions/TX_ID/items \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-000001"}' | python3 -m json.tool

# Complete the transaction
curl -s -X POST http://localhost:8080/transactions/TX_ID/complete \
  -H "Content-Type: application/json" \
  -d '{}' | python3 -m json.tool

# Check low stock
curl -s http://localhost:8080/inventory/low-stock | python3 -m json.tool

# Check popular items
curl -s http://localhost:8080/analytics/popular-items | python3 -m json.tool
```

## Step 6: Run Load Client — Default Mode

```bash
cd load-client
./run.sh --baseUrl=http://localhost:8080 --stations=10 --duration=60
```

**Expected results** (Phase 2 checkpoint):
- 0% error rate across all operations
- All transactions complete successfully
- Low-stock alerts appear for heavily-purchased items
- Popular items list shows Zipf-like distribution
- JSON report written to `load-client/reports/`

**Save this report** for submission as the default-mode result.

## Step 7: Run Load Client — Stress Mode

Restart the monolith (to re-seed fresh data), then:

```bash
cd load-client
./run.sh --baseUrl=http://localhost:8080 --stations=100 --duration=120
```

**Expected results** (Phase 3 checkpoint):
- Error rate < 1%
- Stock correctness invariant holds (no negative stock)
- p95/p99 latencies will be higher than default mode — that's expected
- JSON report written to `load-client/reports/`

**Save this report** for submission as the stress-mode result.

## Step 8: Verify Stock Correctness

After a test run, check the invariant in PostgreSQL:

```sql
-- Connect to the database
psql selfcheckout

-- Check no stock is negative
SELECT COUNT(*) FROM catalog_items WHERE stock < 0;
-- Expected: 0

-- For a specific SKU, verify: initial_stock - final_stock = total completed line items
SELECT
  ci.sku,
  10000 AS initial_stock,
  ci.stock AS final_stock,
  10000 - ci.stock AS stock_consumed,
  COALESCE(tli.total_sold, 0) AS total_sold
FROM catalog_items ci
LEFT JOIN (
  SELECT sku, COUNT(*) AS total_sold
  FROM transaction_line_items tli
  JOIN transactions t ON t.id = tli.transaction_id
  WHERE t.status = 'COMPLETED'
  GROUP BY sku
) tli ON ci.sku = tli.sku
WHERE ci.stock < 10000
ORDER BY ci.stock ASC
LIMIT 20;
-- stock_consumed should equal total_sold for every row
```

## Deliverables Checklist

After completing all steps:

- [ ] `monolith/` — Spring Boot project, builds and runs
- [ ] `quality-attributes.md` — Architectural characteristics analysis (write after testing)
- [ ] `load-client/reports/report-YYYYMMDD-default.json` — Default mode (10 stations, 60s)
- [ ] `load-client/reports/report-YYYYMMDD-stress.json` — Stress mode (100 stations, 120s)
- [ ] Stock correctness invariant verified via SQL query
- [ ] All committed and pushed to GitHub repo
