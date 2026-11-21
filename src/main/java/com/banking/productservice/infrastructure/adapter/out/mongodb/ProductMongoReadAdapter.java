package com.banking.productservice.infrastructure.adapter.out.mongodb;

import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.out.ProductReadPort;
import com.banking.productservice.application.query.ListProductsQuery;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read model over the same {@code products} collection used by the write side. Projects documents
 * into views without rebuilding the aggregate.
 */
@Repository
public class ProductMongoReadAdapter implements ProductReadPort {

  private final ProductMongoRepository repository;
  private final ReactiveMongoTemplate mongoTemplate;
  private final ProductMongoMapper mapper;

  /**
   * Receives the repository for lookups by code, the template for filtered pages and counts, and
   * the document mapper.
   */
  public ProductMongoReadAdapter(
      ProductMongoRepository repository,
      ReactiveMongoTemplate mongoTemplate,
      ProductMongoMapper mapper) {
    this.repository = repository;
    this.mongoTemplate = mongoTemplate;
    this.mapper = mapper;
  }

  @Override
  public Mono<ProductView> findByCode(String code) {
    return repository.findById(code).map(mapper::toView);
  }

  @Override
  public Flux<ProductView> findPage(ListProductsQuery query) {
    // The code is the _id, so ascending code uses the default _id index.
    Query pageQuery =
        filter(query)
            .with(Sort.by(Sort.Direction.ASC, ProductDocument.CODE))
            .skip(query.offset())
            .limit(query.size());

    return mongoTemplate.find(pageQuery, ProductDocument.class).map(mapper::toView);
  }

  @Override
  public Mono<Long> count(ListProductsQuery query) {
    return mongoTemplate.count(filter(query), ProductDocument.class);
  }

  private static Query filter(ListProductsQuery query) {
    Query filter = new Query();
    if (query.family() != null) {
      filter.addCriteria(Criteria.where(ProductDocument.FAMILY).is(query.family()));
    }
    if (query.status() != null) {
      filter.addCriteria(Criteria.where(ProductDocument.STATUS).is(query.status()));
    }
    return filter;
  }
}
