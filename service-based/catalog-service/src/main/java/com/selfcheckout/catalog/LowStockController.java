package com.selfcheckout.catalog;

import com.selfcheckout.shared.catalog.CatalogItem;
import com.selfcheckout.shared.catalog.CatalogItemRepository;
import com.selfcheckout.shared.inventory.InventoryStock;
import com.selfcheckout.shared.inventory.InventoryStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serves {@code GET /inventory/low-stock}. */
@RestController
public class LowStockController {

    private final InventoryStockRepository stock;
    private final CatalogItemRepository catalog;
    private final int defaultThreshold;

    public LowStockController(InventoryStockRepository stock,
                              CatalogItemRepository catalog,
                              @Value("${selfcheckout.inventory.low-stock-threshold:50}") int defaultThreshold) {
        this.stock = stock;
        this.catalog = catalog;
        this.defaultThreshold = defaultThreshold;
    }

    /** A null {@code threshold} means "use the server default". */
    @GetMapping("/inventory/low-stock")
    public Map<String, Object> getLowStock(@RequestParam(required = false) Integer threshold) {
        int effective = threshold == null ? defaultThreshold : threshold;

        List<InventoryStock> low = stock.findByStockLessThan(effective);

        Map<String, String> names = new LinkedHashMap<>();
        for (CatalogItem item : catalog.findAllById(low.stream().map(InventoryStock::getSku).toList())) {
            names.put(item.getSku(), item.getName());
        }

        String triggeredAt = LocalDateTime.now().toString();
        List<Map<String, Object>> alerts = new ArrayList<>(low.size());
        for (InventoryStock s : low) {
            alerts.add(Map.of(
                    "sku", s.getSku(),
                    "name", names.getOrDefault(s.getSku(), s.getSku()),
                    "currentStock", s.getStock(),
                    "threshold", effective,
                    "triggeredAt", triggeredAt));
        }

        return Map.of(
                "threshold", effective,
                "generatedAt", LocalDateTime.now().toString(),
                "alerts", alerts);
    }
}
