package com.banking.productservice.domain.model;

import com.banking.productservice.domain.exception.InvalidProductException;

/**
 * Strategy with the shape the terms of one product family must have: which optional terms it
 * requires and which it forbids. {@link ProductFamily} holds the rule of each family, so a new
 * family brings its own rule instead of another branch in {@link Product}.
 */
public interface FamilyRule {

  /**
   * Checks the terms against the family.
   *
   * @throws InvalidProductException when a required term is missing or a term that does not apply
   *     to the family is present
   */
  void check(ProductTerms terms);

  /** Fails with {@code message} when {@code value} is missing. */
  static void requires(Object value, String message) {
    if (value == null) {
      throw new InvalidProductException(message);
    }
  }

  /** Fails with {@code message} when {@code value} is present. */
  static void forbids(Object value, String message) {
    if (value != null) {
      throw new InvalidProductException(message);
    }
  }
}
