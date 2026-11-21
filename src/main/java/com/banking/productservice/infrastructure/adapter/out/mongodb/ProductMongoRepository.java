package com.banking.productservice.infrastructure.adapter.out.mongodb;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Spring Data access to the {@code products} collection, keyed by product code. */
public interface ProductMongoRepository extends ReactiveMongoRepository<ProductDocument, String> {}
