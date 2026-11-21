package com.banking.productservice.application.handler.query;

import com.banking.productservice.application.model.ProductPage;
import com.banking.productservice.application.port.in.ListProductsUseCase;
import com.banking.productservice.application.port.out.ProductReadPort;
import com.banking.productservice.application.query.ListProductsQuery;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Reads one page of the catalog ordered by code, optionally filtered by family and status, with the
 * total of matching products.
 */
public class ListProductsHandler implements ListProductsUseCase {

  private final ProductReadPort readPort;

  public ListProductsHandler(ProductReadPort readPort) {
    this.readPort = Objects.requireNonNull(readPort);
  }

  @Override
  public Mono<ProductPage> execute(ListProductsQuery query) {
    // Items and count run concurrently; an offset past the last match yields no items.
    return Mono.zip(readPort.findPage(query).collectList(), readPort.count(query))
        .map(result -> ProductPage.of(result.getT1(), query.page(), query.size(), result.getT2()));
  }
}
