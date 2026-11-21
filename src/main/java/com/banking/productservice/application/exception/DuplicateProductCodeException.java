package com.banking.productservice.application.exception;

/** The code already identifies a product, active or retired. */
public class DuplicateProductCodeException extends RuntimeException {
  public DuplicateProductCodeException(Throwable cause) {
    super("A product with this code already exists.", cause);
  }
}
