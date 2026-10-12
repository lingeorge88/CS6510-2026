package com.selfcheckout.shared.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for the catalog_items table. */
public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {
}
