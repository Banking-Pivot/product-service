package com.banking.productservice.application.exception;

/**
 * The revision supplied by the client no longer matches the stored one, either before the write or
 * because another writer won the race.
 */
public class ProductConcurrentModificationException extends RuntimeException {
  public ProductConcurrentModificationException(String code) {
    super("Product " + code + " changed. Read it again before retrying.");
  }
}
