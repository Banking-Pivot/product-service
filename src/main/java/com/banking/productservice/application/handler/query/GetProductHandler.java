package com.banking.productservice.application.handler.query;

import com.banking.productservice.application.exception.ProductNotFoundException;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.in.GetProductUseCase;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.application.port.out.ProductReadPort;
import com.banking.productservice.application.query.GetProductQuery;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Reads a product through the cache: a cached product is returned without touching the store;
 * otherwise it is read from the store and cached for the next reads. Products that do not exist are
 * not cached.
 */
public class GetProductHandler implements GetProductUseCase {

  private final ProductReadPort readPort;
  private final ProductCachePort cache;

  public GetProductHandler(ProductReadPort readPort, ProductCachePort cache) {
    this.readPort = Objects.requireNonNull(readPort);
    this.cache = Objects.requireNonNull(cache);
  }

  @Override
  public Mono<ProductView> execute(GetProductQuery query) {
    return cache
        .find(query.code())
        .switchIfEmpty(
            Mono.defer(
                () ->
                    readPort
                        .findByCode(query.code())
                        .flatMap(view -> cache.store(view).thenReturn(view))))
        .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(query.code())));
  }
}
