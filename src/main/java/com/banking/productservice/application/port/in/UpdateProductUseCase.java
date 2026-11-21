package com.banking.productservice.application.port.in;

import com.banking.productservice.application.command.UpdateProductCommand;
import com.banking.productservice.application.model.ProductView;
import reactor.core.publisher.Mono;

/**
 * Replaces name, eligibility and terms of an active product if the revision the client read is
 * still the stored one, and returns the product with its current revision.
 */
public interface UpdateProductUseCase {
  Mono<ProductView> execute(UpdateProductCommand command);
}
