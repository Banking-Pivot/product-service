package com.banking.productservice.application.port.out;

import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.query.ListProductsQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read side of the product store. Projects stored data into views without loading the aggregate.
 */
public interface ProductReadPort {

  /** Emits the product view, or completes empty when it does not exist. */
  Mono<ProductView> findByCode(String code);

  /** Emits one page of matching products ordered by code ascending. */
  Flux<ProductView> findPage(ListProductsQuery query);

  /** Counts the products matching the query filters. */
  Mono<Long> count(ListProductsQuery query);
}
