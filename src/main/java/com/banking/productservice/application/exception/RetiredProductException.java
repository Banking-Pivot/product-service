package com.banking.productservice.application.exception;

/** The product is retired, so its name, eligibility and terms can no longer be edited. */
public class RetiredProductException extends RuntimeException {
  public RetiredProductException(String code) {
    super("Product " + code + " is retired and cannot be updated.");
  }
}
