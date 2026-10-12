# Service-Based Architecture

Coarse-grained **domain services** decomposed by business function, each an
independently deployable container, sharing **one centralized database**.


## Services

- **catalog-service** (Spring Boot) — owns `catalog_items` and `inventory_stock`; serves `GET /items` and `GET /inventory/low-stock`.
- **checkout-service** (Spring Boot) — owns `transactions` and `transaction_line_items`; serves `POST /transactions`, `/{id}/items`, `/{id}/complete`, and `GET /transactions/{id}`.
- **analytics-service** (Spring Boot) — owns `popular_items_snapshot`; serves `GET /analytics/popular-items`.


## Routing

The api-gateway at (`:8080`) path-routes each endpoint to the
corresponding service.

- `/items`, `/inventory/low-stock` → **catalog-service**
- `/transactions*` → **checkout-service**
- `/analytics/popular-items` → **analytics-service**

## Database access

`shared-persistence` is a thin library holding **only** the entities/repositories
for tables touched by more than one service:

- `catalog_items` — read by catalog, checkout, analytics
- `inventory_stock` — written by checkout, read by catalog
- `scan_events` — written by checkout, read by analytics


## Topology

```mermaid
flowchart TB
    client["Load client"]:::ext --> gw["api-gateway<br/>nginx :8080"]:::gw

    gw -->|"/items · /inventory/"| cat["catalog-service<br/>GET /items<br/>GET /inventory/low-stock"]:::cat
    gw -->|"/transactions"| chk["checkout-service<br/>POST /transactions<br/>POST /transactions/{id}/items<br/>POST /transactions/{id}/complete<br/>GET /transactions/{id}"]:::chk
    gw -->|"/analytics/"| ana["analytics-service<br/>GET /analytics/popular-items"]:::ana

    subgraph schema["Shared schema — tables"]
        direction LR
        t_cat["catalog_items"]:::tbl
        t_inv["inventory_stock"]:::tbl
        t_tx["transactions"]:::tbl
        t_li["transaction_line_items"]:::tbl
        t_scan["scan_events"]:::tbl
        t_snap["popular_items_snapshot"]:::tbl
    end

    cat --> t_cat
    cat --> t_inv
    chk --> t_tx
    chk --> t_li
    chk -->|"read price/name"| t_cat
    chk -->|"atomic decrement<br/>UPDATE … WHERE stock>0"| t_inv
    chk -->|"write scan"| t_scan
    ana -->|"read + rank window"| t_scan
    ana --> t_snap

    t_cat --> pg[("PostgreSQL DB selfcheckout")]:::db
    t_inv --> pg
    t_tx --> pg
    t_li --> pg
    t_scan --> pg
    t_snap --> pg

    classDef ext fill:#e2e8f0,stroke:#475569,color:#0f172a;
    classDef gw  fill:#fde68a,stroke:#d97706,color:#78350f;
    classDef cat fill:#bfdbfe,stroke:#2563eb,color:#1e3a8a;
    classDef chk fill:#bbf7d0,stroke:#16a34a,color:#14532d;
    classDef ana fill:#e9d5ff,stroke:#9333ea,color:#581c87;
    classDef tbl fill:#ffffff,stroke:#64748b,color:#0f172a;
    classDef db  fill:#fecaca,stroke:#dc2626,color:#7f1d1d;
```


## Building and Running Different Services

```mermaid
flowchart LR
    mvn["mvn package"]:::a --> jars["4 jars<br/>lib + 3 services"]:::a
    jars --> build["docker compose build<br/>jar → image"]:::b
    build --> up["docker compose up"]:::b
    up --> run["5 containers up<br/>db + 3 services + gateway :8080"]:::c

    classDef a fill:#bfdbfe,stroke:#2563eb,color:#1e3a8a;
    classDef b fill:#fde68a,stroke:#d97706,color:#78350f;
    classDef c fill:#bbf7d0,stroke:#16a34a,color:#14532d;
```

Each service compiles to a Spring Boot fat jar (with `shared-persistence` bundled
in); `docker compose build` copies each jar into its own image; `docker compose
up` starts Postgres (which runs schema + seed on first init), then the services
once the DB is healthy, then the gateway.

```bash
# 1. Build all jars (host)
cd service-based
mvn -DskipTests package

# 2. Build images and start db + 3 services + gateway
docker compose up --build         # add -d to detach

# 3. Point the load client at the gateway
cd ../load-client
./build.sh
./run.sh --baseUrl=http://localhost:8080 --stations=10 --duration=60
```

Fresh, reseeded database each run:

```bash
docker compose down && docker compose up --build
```

