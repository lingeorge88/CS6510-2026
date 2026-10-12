package com.selfcheckout.catalog;

import com.selfcheckout.shared.catalog.CatalogItem;
import com.selfcheckout.shared.catalog.CatalogItemRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Serves {@code GET /items}, the full catalog. */
@RestController
public class CatalogController {

    private final CatalogItemRepository catalog;

    public CatalogController(CatalogItemRepository catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/items")
    public Map<String, List<CatalogItem>> getAllItems() {
        return Map.of("items", catalog.findAll());
    }
}
