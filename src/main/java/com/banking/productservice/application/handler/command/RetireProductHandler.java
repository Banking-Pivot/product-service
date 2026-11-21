package com.banking.productservice.application.handler.command;

import com.banking.productservice.application.command.RetireProductCommand;
import com.banking.productservice.application.exception.ProductConcurrentModificationException;
import com.banking.productservice.application.exception.ProductNotFoundException;
import com.banking.productservice.application.port.in.RetireProductUseCase;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.model.Product;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Logical deletion: ACTIVE becomes RETIRED, the record and its code stay reserved. Repeating it
 * with the current revision is a no-op; repeating it with an old revision is a stale precondition
 * (412). A retirement removes the product from the cache after it is stored.
 */
public class RetireProductHandler implements RetireProductUseCase {

  private final ProductWritePort writePort;
  private final ProductCachePort cache;
  private final Clock clock;

  /** Receives the product store, the product cache and the clock that dates the retirement. */
  public RetireProductHandler(ProductWritePort writePort, ProductCachePort cache, Clock clock) {
    this.writePort = Objects.requireNonNull(writePort);
    this.cache = Objects.requireNonNull(cache);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<Void> execute(RetireProductCommand command) {
    return writePort
        .findByCode(command.code())
        .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(command.code())))
        .flatMap(product -> apply(product, command));
  }

  private Mono<Void> apply(Product product, RetireProductCommand command) {
    if (product.getVersion() != command.expectedVersion()) {
      return Mono.error(new ProductConcurrentModificationException(product.getCode()));
    }

    boolean changed = product.retire(clock.instant().truncatedTo(ChronoUnit.MILLIS));

    if (!changed) {
      return Mono.empty();
    }

    return writePort
        .update(product, command.expectedVersion())
        .switchIfEmpty(
            Mono.error(() -> new ProductConcurrentModificationException(product.getCode())))
        .flatMap(saved -> cache.evict(saved.getCode()));
  }
}
