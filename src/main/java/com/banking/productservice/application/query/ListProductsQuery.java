package com.banking.productservice.application.query;

import com.banking.productservice.domain.model.ProductFamily;
import com.banking.productservice.domain.model.ProductStatus;

/**
 * Zero-based page request with optional filters; {@code null} means "any". Bounds mirror the
 * contract: page 0..10000, size 1..100.
 */
public record ListProductsQuery(int page, int size, ProductFamily family, ProductStatus status) {
  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_PAGE = 10_000;
  public static final int MAX_SIZE = 100;

  /** Rejects a page outside 0..{@link #MAX_PAGE} and a size outside 1..{@link #MAX_SIZE}. */
  public ListProductsQuery {
    if (page < 0 || page > MAX_PAGE) {
      throw new IllegalArgumentException("Page must be between 0 and " + MAX_PAGE + ".");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("Size must be between 1 and " + MAX_SIZE + ".");
    }
  }

  public long offset() {
    return (long) page * size;
  }
}
