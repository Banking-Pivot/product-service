package com.banking.productservice.application.port.in;

import com.banking.productservice.application.command.CreateProductCommand;
import com.banking.productservice.application.model.ProductView;
import reactor.core.publisher.Mono;

/** Adds a product to the catalog and returns it with its first revision. */
public interface CreateProductUseCase {
  Mono<ProductView> execute(CreateProductCommand command);
}
