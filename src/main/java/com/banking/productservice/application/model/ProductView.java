package com.banking.productservice.application.model;

import com.banking.productservice.domain.model.Product;
import com.banking.productservice.domain.model.enums.ProductFamily;
import com.banking.productservice.domain.model.enums.ProductStatus;
import com.banking.productservice.domain.model.vo.Eligibility;
import com.banking.productservice.domain.model.vo.ProductTerms;
import java.time.Instant;

/**
 * Read representation of a product returned by every use case, and the value kept in the cache.
 * {@code version} is the persisted revision exposed to HTTP clients as ETag.
 */
public record ProductView(
    String code,
    String name,
    ProductFamily family,
    Eligibility eligibility,
    ProductTerms terms,
    ProductStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version) {
  /** Copies the current state of the aggregate, including the revision it was loaded at. */
  public static ProductView from(Product product) {
    return new ProductView(
        product.getCode(),
        product.getName(),
        product.getFamily(),
        product.getEligibility(),
        product.getTerms(),
        product.getStatus(),
        product.getCreatedAt(),
        product.getUpdatedAt(),
        product.getVersion());
  }
}
