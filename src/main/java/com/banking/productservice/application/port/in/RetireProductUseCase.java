package com.banking.productservice.application.port.in;

import com.banking.productservice.application.command.RetireProductCommand;
import reactor.core.publisher.Mono;

/**
 * Retires a product if the revision the client read is still the stored one. The record and its
 * code are kept.
 */
public interface RetireProductUseCase {
  Mono<Void> execute(RetireProductCommand command);
}
