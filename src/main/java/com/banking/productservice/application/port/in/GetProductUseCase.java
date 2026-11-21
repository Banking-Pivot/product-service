package com.banking.productservice.application.port.in;

import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.query.GetProductQuery;
import reactor.core.publisher.Mono;

/** Reads one product by its code, whether it is active or retired. */
public interface GetProductUseCase {
  Mono<ProductView> execute(GetProductQuery query);
}
