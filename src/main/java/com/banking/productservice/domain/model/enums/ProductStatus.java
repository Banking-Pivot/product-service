package com.banking.productservice.domain.model.enums;

/**
 * Lifecycle of a catalog product. RETIRED is the logical deletion: the record and its code are
 * kept, and the product can no longer be edited.
 */
public enum ProductStatus {
  ACTIVE,
  RETIRED
}
