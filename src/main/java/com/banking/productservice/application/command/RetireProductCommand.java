package com.banking.productservice.application.command;

import java.util.Objects;

/** Logical deletion, conditioned on the revision the client last read. */
public record RetireProductCommand(String code, long expectedVersion) {
  /** Rejects a missing product code and a negative expected revision. */
  public RetireProductCommand {
    Objects.requireNonNull(code, "Product code is required.");
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("Expected version must not be negative.");
    }
  }
}
