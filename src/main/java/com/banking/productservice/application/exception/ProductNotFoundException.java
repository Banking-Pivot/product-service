package com.banking.productservice.application.exception;

/** No product, active or retired, has the requested code. */
public class ProductNotFoundException extends RuntimeException {
  public ProductNotFoundException(String code) {
    super("Product " + code + " does not exist.");
  }
}
