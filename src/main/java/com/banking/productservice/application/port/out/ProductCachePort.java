package com.banking.productservice.application.port.out;

import com.banking.productservice.application.model.ProductView;
import reactor.core.publisher.Mono;

/**
 * Cache of product views by code. It only makes reads faster: no method fails when the cache is
 * unreachable, so a request is never rejected because of it. A read then falls back to the store,
 * and a product that could not be removed stays until it expires.
 */
public interface ProductCachePort {

  /** Emits the cached view, or completes empty when it is not cached or the cache is down. */
  Mono<ProductView> find(String code);

  /** Keeps the view for the configured time. */
  Mono<Void> store(ProductView view);

  /** Removes the product, so the next read loads its current revision from the store. */
  Mono<Void> evict(String code);
}
