#!/bin/bash
# Repeated load-test harness. Run-to-run variance on this workload is large
# (an 18% spread between two identical runs was observed), so a single run
# cannot support a claim. Usage: ./bench.sh <label> <reps>
set -u
LABEL=${1:-run}; REPS=${2:-3}
HERE=$(cd "$(dirname "$0")" && pwd)
CLIENT="$HERE/../load-client"

for i in $(seq 1 "$REPS"); do
  kill "$(lsof -ti:8080)" 2>/dev/null; sleep 2
  (cd "$HERE" && nohup ./mvnw -B spring-boot:run > /tmp/layered-boot.log 2>&1 &)
  until curl -sf http://localhost:8080/items -o /dev/null 2>/dev/null; do sleep 1; done

  # Warm JIT and page cache, then reset tables so every measured run starts
  # from an empty transaction_line_items.
  (cd "$CLIENT" && ./run.sh --baseUrl=http://localhost:8080 --stations=10 --duration=20 >/dev/null 2>&1)
  psql selfcheckout -q -c "TRUNCATE transaction_line_items, scan_events, transactions, popular_items_snapshot;
                           UPDATE catalog_items SET stock=10000; VACUUM ANALYZE;" >/dev/null 2>&1

  OUT=$(cd "$CLIENT" && ./run.sh --baseUrl=http://localhost:8080 --stations=10 --duration=60 2>&1)
  echo "$LABEL rep$i tps=$(echo "$OUT" | grep -oE '\([0-9.]+/sec\)' | head -1 | tr -d '(/sec)')" \
       "complete_p50=$(echo "$OUT" | awk '/COMPLETE_TRANSACTION/{print $6}')" \
       "complete_p95=$(echo "$OUT" | awk '/COMPLETE_TRANSACTION/{print $7}')" \
       "scan_p50=$(echo "$OUT" | awk '/SCAN_ITEM/{print $6}')"
done
kill "$(lsof -ti:8080)" 2>/dev/null

echo "--- stock invariant ---"
psql selfcheckout -c "
WITH sold AS (SELECT li.sku, COUNT(*)::bigint u FROM transaction_line_items li
  JOIN transactions t ON t.transaction_id=li.transaction_id
  WHERE t.status='COMPLETED' GROUP BY li.sku)
SELECT count(*) FILTER (WHERE (10000-c.stock) <> COALESCE(s.u,0)) AS skus_violating,
       COALESCE(sum(COALESCE(s.u,0)-(10000-c.stock)),0)            AS units_never_decremented,
       count(*) FILTER (WHERE c.stock < 0)                         AS negative_stock
FROM catalog_items c LEFT JOIN sold s ON s.sku=c.sku;"
