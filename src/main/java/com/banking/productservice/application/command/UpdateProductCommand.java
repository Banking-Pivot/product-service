package com.banking.productservice.application.command;

import com.banking.productservice.domain.model.Eligibility;
import com.banking.productservice.domain.model.ProductTerms;
import java.util.Objects;

/** Full replacement of the editable fields, conditioned on the revision the client last read. */
public record UpdateProductCommand(
    String code, String name, Eligibility eligibility, ProductTerms terms, long expectedVersion) {
  /**
   * Rejects a missing product code and a negative expected revision. Name, eligibility and terms
   * are validated by the aggregate when the handler applies them.
   */
  public UpdateProductCommand {
    Objects.requireNonNull(code, "Product code is required.");
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("Expected version must not be negative.");
    }
  }
}
