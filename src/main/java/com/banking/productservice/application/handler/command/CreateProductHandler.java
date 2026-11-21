package com.banking.productservice.application.handler.command;

import com.banking.productservice.application.command.CreateProductCommand;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.in.CreateProductUseCase;
import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.model.Product;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Adds a product to the catalog as ACTIVE at revision 0. The aggregate validates the values (422 on
 * a rule violation) and the store rejects a code that is already taken, by an active or a retired
 * product (409).
 */
public class CreateProductHandler implements CreateProductUseCase {

  private final ProductWritePort writePort;
  private final Clock clock;

  public CreateProductHandler(ProductWritePort writePort, Clock clock) {
    this.writePort = Objects.requireNonNull(writePort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<ProductView> execute(CreateProductCommand command) {
    // defer: domain validation errors become onError signals, not thrown exceptions.
    return Mono.defer(
            () -> {
              // MongoDB stores milliseconds; truncating keeps the response equal to the stored
              // value.
              Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);

              Product product =
                  Product.create(
                      command.code(),
                      command.name(),
                      command.family(),
                      command.eligibility(),
                      command.terms(),
                      now);

              // The code is the document ID, so the store decides collisions atomically.
              // A new code is in no cache yet: nothing to remove.
              return writePort.insert(product);
            })
        .map(ProductView::from);
  }
}
