package com.selfcheckout.shared.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read access to the catalog. Shared because Catalog, Checkout, and Analytics all
 * read catalog_items. This system has no catalog writes, so the repository is
 * read-only by convention; Catalog Service remains the logical write-owner.
 */
public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {
}
