package com.banking.productservice.application.port.out;

import com.banking.productservice.domain.model.Product;
import reactor.core.publisher.Mono;

/** Write side of the product store. Loads aggregates and persists their changes. */
public interface ProductWritePort {

  /**
   * Inserts a new product and emits it with its persisted revision. Fails with {@code
   * DuplicateProductCodeException} when the code already exists, including on retired products.
   */
  Mono<Product> insert(Product product);

  /** Loads the aggregate, or completes empty when the product does not exist. */
  Mono<Product> findByCode(String code);

  /**
   * Persists name, eligibility, terms, status and updatedAt only if the stored revision still
   * equals {@code expectedVersion}, incrementing it in the same atomic operation. Emits the saved
   * product with its new revision, or completes empty when the stored revision no longer matches.
   *
   * @param expectedVersion revision the client sent in If-Match; it is the write condition even if
   *     the aggregate was re-read in between.
   */
  Mono<Product> update(Product product, long expectedVersion);
}
