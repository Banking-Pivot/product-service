package com.banking.productservice.application.model;

import java.util.List;

/**
 * One page of products. Items and total are read separately, so concurrent writes may make them
 * disagree; the contract documents that pages are not snapshots.
 */
public record ProductPage(
    List<ProductView> items, int page, int size, long totalElements, long totalPages) {
  public ProductPage {
    items = List.copyOf(items);
  }

  public static ProductPage of(List<ProductView> items, int page, int size, long totalElements) {
    long totalPages = totalElements == 0 ? 0 : (totalElements + size - 1) / size;
    return new ProductPage(items, page, size, totalElements, totalPages);
  }
}
