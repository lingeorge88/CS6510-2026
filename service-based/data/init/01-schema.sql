-- ============================================================================
-- Centralized shared database schema for the service-based architecture.
--
-- The schema is owned by the DATABASE, not by any single service: it is created
-- once by the Postgres container's init process (docker-entrypoint-initdb.d),
-- and every service connects with ddl-auto: none. This is the honest "single
-- centralized database" of service-based architecture — services are logically
-- partitioned into data domains (see comments below) but share one physical DB.
--
-- Data-domain ownership (logical partitioning):
--   catalog_items          -> Catalog Service     (read by Checkout + Analytics)
--   inventory_stock        -> Inventory Service    (written by Checkout at completion)
--   transactions           -> Checkout Service     (private)
--   transaction_line_items -> Checkout Service     (private)
--   scan_events            -> Analytics Service     (written by Checkout at scan time)
--   popular_items_snapshot -> Analytics Service     (private)
-- ============================================================================

-- --- Catalog data domain -----------------------------------------------------
CREATE TABLE catalog_items (
    sku   VARCHAR(20)    NOT NULL PRIMARY KEY,
    name  VARCHAR(255)   NOT NULL,
    price NUMERIC(10, 2) NOT NULL
);

-- --- Inventory data domain ---------------------------------------------------
-- stock is split out of catalog_items into its own table so Inventory owns a
-- real data domain. The CHECK is a belt-and-suspenders backstop behind the
-- atomic conditional UPDATE that Checkout issues (stock can never go negative).
CREATE TABLE inventory_stock (
    sku   VARCHAR(20) NOT NULL PRIMARY KEY,
    stock INT         NOT NULL DEFAULT 0 CHECK (stock >= 0)
);

-- --- Checkout data domain ----------------------------------------------------
CREATE TABLE transactions (
    transaction_id VARCHAR(50)    NOT NULL PRIMARY KEY,
    station_id     VARCHAR(50)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    item_count     INT            NOT NULL,
    running_total  NUMERIC(10, 2) NOT NULL,
    started_at     TIMESTAMP      NOT NULL,
    completed_at   TIMESTAMP
);

CREATE TABLE transaction_line_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id VARCHAR(50)    NOT NULL,
    sku            VARCHAR(20)    NOT NULL,
    unit_price     NUMERIC(10, 2) NOT NULL
);
CREATE INDEX idx_line_item_transaction_id ON transaction_line_items (transaction_id);

-- --- Analytics data domain ---------------------------------------------------
-- id is an identity column and doubles as the monotonic global scan sequence
-- that defines the hopping window (windowStart/windowEnd are id values).
CREATE TABLE scan_events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku        VARCHAR(20) NOT NULL,
    scanned_at TIMESTAMP   NOT NULL
);

CREATE TABLE popular_items_snapshot (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    window_start BIGINT       NOT NULL,
    window_end   BIGINT       NOT NULL,
    computed_at  TIMESTAMP    NOT NULL,
    sku          VARCHAR(20)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    scan_count   INT          NOT NULL,
    rank         INT          NOT NULL
);
CREATE INDEX idx_snapshot_window_end_rank ON popular_items_snapshot (window_end, rank);
