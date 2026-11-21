package com.banking.productservice.infrastructure.adapter.out.mongodb;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.banking.productservice.application.exception.DuplicateProductCodeException;
import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.model.Eligibility;
import com.banking.productservice.domain.model.Product;
import com.banking.productservice.domain.model.ProductTerms;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Write side of the product store on MongoDB. An insert never overwrites an existing code, and an
 * update is a compare-and-set on the revision that increments it in the same operation.
 */
@Repository
public class ProductMongoWriteAdapter implements ProductWritePort {

  private final ProductMongoRepository repository;
  private final ReactiveMongoTemplate mongoTemplate;
  private final ProductMongoMapper mapper;

  /**
   * Receives the repository for inserts and lookups by code, the template for conditional updates,
   * and the document mapper.
   */
  public ProductMongoWriteAdapter(
      ProductMongoRepository repository,
      ReactiveMongoTemplate mongoTemplate,
      ProductMongoMapper mapper) {
    this.repository = repository;
    this.mongoTemplate = mongoTemplate;
    this.mapper = mapper;
  }

  @Override
  public Mono<Product> insert(Product product) {
    // insert (not save) so an existing code is never overwritten; @Version starts at 0.
    return repository
        .insert(mapper.toDocument(product))
        .map(mapper::toDomain)
        .onErrorMap(DuplicateKeyException.class, DuplicateProductCodeException::new);
  }

  @Override
  public Mono<Product> findByCode(String code) {
    return repository.findById(code).map(mapper::toDomain);
  }

  @Override
  public Mono<Product> update(Product product, long expectedVersion) {
    // Code and family are never part of the update.
    Eligibility eligibility = product.getEligibility();
    ProductTerms terms = product.getTerms();
    var changes =
        new Update()
            .set(ProductDocument.NAME, product.getName())
            .set(ProductDocument.STATUS, product.getStatus())
            .set(ProductDocument.CUSTOMER_TYPES, mapper.toStored(eligibility))
            .set(ProductDocument.REQUIRES_CREDIT_CARD, eligibility.requiresCreditCard())
            .set(ProductDocument.OPENING_MINIMUM, mapper.toStored(terms.openingMinimum()))
            .set(ProductDocument.FREE_MONTHLY_MOVEMENTS, terms.freeMonthlyMovements())
            .set(ProductDocument.MOVEMENT_FEE, mapper.toStored(terms.movementFee()))
            .set(ProductDocument.UPDATED_AT, product.getUpdatedAt())
            .inc(ProductDocument.VERSION, 1);
    setOrUnset(changes, ProductDocument.REQUIRED_PROFILE, eligibility.requiredProfile());
    setOrUnset(changes, ProductDocument.MONTHLY_MOVEMENT_LIMIT, terms.monthlyMovementLimit());
    setOrUnset(
        changes,
        ProductDocument.MONTHLY_MAINTENANCE_FEE,
        mapper.toStored(terms.monthlyMaintenanceFee()));
    setOrUnset(
        changes,
        ProductDocument.MINIMUM_DAILY_AVERAGE_BALANCE,
        mapper.toStored(terms.minimumDailyAverageBalance()));
    setOrUnset(changes, ProductDocument.OPERATION_DAY_OF_MONTH, terms.operationDayOfMonth());

    // Compare-and-set in a single document operation: the filter carries the
    // client's revision, so a concurrent writer makes this match nothing.
    var filter =
        query(
            where(ProductDocument.CODE)
                .is(product.getCode())
                .and(ProductDocument.VERSION)
                .is(expectedVersion));

    return mongoTemplate
        .findAndModify(
            filter, changes, FindAndModifyOptions.options().returnNew(true), ProductDocument.class)
        .map(mapper::toDomain);
  }

  /** A term that stops applying leaves the document, as if it had never been set. */
  private static void setOrUnset(Update changes, String field, Object value) {
    if (value == null) {
      changes.unset(field);
    } else {
      changes.set(field, value);
    }
  }
}
