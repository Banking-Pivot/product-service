package com.banking.productservice.application.port.in;

import com.banking.productservice.application.model.ProductPage;
import com.banking.productservice.application.query.ListProductsQuery;
import reactor.core.publisher.Mono;

/** Reads one page of the catalog, optionally filtered by family and status. */
public interface ListProductsUseCase {
  Mono<ProductPage> execute(ListProductsQuery query);
}
