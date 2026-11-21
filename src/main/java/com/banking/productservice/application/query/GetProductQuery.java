package com.banking.productservice.application.query;

import java.util.Objects;

/** Lookup of one product by its code. */
public record GetProductQuery(String code) {
  public GetProductQuery {
    Objects.requireNonNull(code, "Product code is required.");
  }
}
