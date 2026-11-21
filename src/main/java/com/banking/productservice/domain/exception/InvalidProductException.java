package com.banking.productservice.domain.exception;

/** A product, its eligibility or its terms break a business rule. */
public class InvalidProductException extends RuntimeException {
  public InvalidProductException(String message) {
    super(message);
  }
}
