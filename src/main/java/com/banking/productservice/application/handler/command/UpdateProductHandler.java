package com.banking.productservice.application.handler.command;

import com.banking.productservice.application.command.UpdateProductCommand;
import com.banking.productservice.application.exception.ProductConcurrentModificationException;
import com.banking.productservice.application.exception.ProductNotFoundException;
import com.banking.productservice.application.exception.RetiredProductException;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.in.UpdateProductUseCase;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.model.Product;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Replaces name, eligibility and terms of an active product.
 *
 * <p>Evaluation order: 404 missing, 412 stale revision, 409 retired, 422 rule violation. The
 * precondition is checked before the state so a client holding an old revision always re-reads
 * first.
 *
 * <p>A change removes the product from the cache after it is stored, so the next read returns the
 * new revision.
 */
public class UpdateProductHandler implements UpdateProductUseCase {

  private final ProductWritePort writePort;
  private final ProductCachePort cache;
  private final Clock clock;

  /** Receives the product store, the product cache and the clock that dates the change. */
  public UpdateProductHandler(ProductWritePort writePort, ProductCachePort cache, Clock clock) {
    this.writePort = Objects.requireNonNull(writePort);
    this.cache = Objects.requireNonNull(cache);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<ProductView> execute(UpdateProductCommand command) {
    return writePort
        .findByCode(command.code())
        .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(command.code())))
        .flatMap(product -> apply(product, command))
        .map(ProductView::from);
  }

  private Mono<Product> apply(Product product, UpdateProductCommand command) {
    if (product.getVersion() != command.expectedVersion()) {
      return Mono.error(new ProductConcurrentModificationException(product.getCode()));
    }
    if (!product.isActive()) {
      return Mono.error(new RetiredProductException(product.getCode()));
    }

    boolean changed =
        product.update(
            command.name(),
            command.eligibility(),
            command.terms(),
            clock.instant().truncatedTo(ChronoUnit.MILLIS));

    if (!changed) {
      // Same revision, same values: keep the revision and return the current state.
      return Mono.just(product);
    }

    // The client's revision, not the re-read one, is the write condition.
    return writePort
        .update(product, command.expectedVersion())
        .switchIfEmpty(
            Mono.error(() -> new ProductConcurrentModificationException(product.getCode())))
        .flatMap(saved -> cache.evict(saved.getCode()).thenReturn(saved));
  }
}
