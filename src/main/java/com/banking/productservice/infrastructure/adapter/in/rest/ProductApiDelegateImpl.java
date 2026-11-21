package com.banking.productservice.infrastructure.adapter.in.rest;

import com.banking.productservice.application.command.RetireProductCommand;
import com.banking.productservice.application.exception.ProductConcurrentModificationException;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.in.CreateProductUseCase;
import com.banking.productservice.application.port.in.GetProductUseCase;
import com.banking.productservice.application.port.in.ListProductsUseCase;
import com.banking.productservice.application.port.in.RetireProductUseCase;
import com.banking.productservice.application.port.in.UpdateProductUseCase;
import com.banking.productservice.application.query.GetProductQuery;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.api.ProductsApiDelegate;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.CreateProductRequest;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductFamily;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductPageResponse;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductResponse;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductStatus;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.UpdateProductRequest;
import java.net.URI;
import java.util.OptionalLong;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Implements the contract-generated delegate. The generated {@code ProductsApiController} receives
 * HTTP requests, validates parameters and calls these methods; this class only translates and
 * chooses status codes.
 */
@Component
public class ProductApiDelegateImpl implements ProductsApiDelegate {

  private final CreateProductUseCase createProductUseCase;
  private final GetProductUseCase getProductUseCase;
  private final ListProductsUseCase listProductsUseCase;
  private final UpdateProductUseCase updateProductUseCase;
  private final RetireProductUseCase retireProductUseCase;
  private final ProductRestMapper mapper;

  /**
   * Receives the five product use cases and the mapper between REST DTOs and application records.
   */
  public ProductApiDelegateImpl(
      CreateProductUseCase createProductUseCase,
      GetProductUseCase getProductUseCase,
      ListProductsUseCase listProductsUseCase,
      UpdateProductUseCase updateProductUseCase,
      RetireProductUseCase retireProductUseCase,
      ProductRestMapper mapper) {
    this.createProductUseCase = createProductUseCase;
    this.getProductUseCase = getProductUseCase;
    this.listProductsUseCase = listProductsUseCase;
    this.updateProductUseCase = updateProductUseCase;
    this.retireProductUseCase = retireProductUseCase;
    this.mapper = mapper;
  }

  @Override
  public Mono<ResponseEntity<ProductResponse>> createProduct(
      Mono<CreateProductRequest> request, ServerWebExchange exchange) {
    return request
        .map(mapper::toCommand)
        .flatMap(createProductUseCase::execute)
        .map(
            view ->
                ResponseEntity.created(URI.create("/api/v1/products/" + view.code()))
                    .eTag(mapper.toEtag(view.version()))
                    .body(mapper.toResponse(view)));
  }

  @Override
  public Mono<ResponseEntity<ProductResponse>> getProduct(
      String productCode, ServerWebExchange exchange) {
    return getProductUseCase.execute(new GetProductQuery(productCode)).map(this::okWithEtag);
  }

  @Override
  public Mono<ResponseEntity<ProductPageResponse>> listProducts(
      Integer page,
      Integer size,
      ProductFamily family,
      ProductStatus status,
      ServerWebExchange exchange) {
    return Mono.fromSupplier(() -> mapper.toQuery(page, size, family, status))
        .flatMap(listProductsUseCase::execute)
        .map(result -> ResponseEntity.ok(mapper.toPageResponse(result)));
  }

  @Override
  public Mono<ResponseEntity<ProductResponse>> updateProduct(
      String productCode,
      String ifMatch,
      Mono<UpdateProductRequest> request,
      ServerWebExchange exchange) {
    return request
        .flatMap(
            body -> {
              OptionalLong expectedVersion = mapper.toExpectedVersion(ifMatch);
              if (expectedVersion.isEmpty()) {
                return unmatchableRevision(productCode);
              }
              return updateProductUseCase.execute(
                  mapper.toCommand(productCode, expectedVersion.getAsLong(), body));
            })
        .map(this::okWithEtag);
  }

  @Override
  public Mono<ResponseEntity<Void>> retireProduct(
      String productCode, String ifMatch, ServerWebExchange exchange) {
    ResponseEntity<Void> noContent = ResponseEntity.noContent().build();

    return Mono.defer(
            () -> {
              OptionalLong expectedVersion = mapper.toExpectedVersion(ifMatch);
              if (expectedVersion.isEmpty()) {
                return unmatchableRevision(productCode).then();
              }
              return retireProductUseCase.execute(
                  new RetireProductCommand(productCode, expectedVersion.getAsLong()));
            })
        .thenReturn(noContent);
  }

  private ResponseEntity<ProductResponse> okWithEtag(ProductView view) {
    return ResponseEntity.ok().eTag(mapper.toEtag(view.version())).body(mapper.toResponse(view));
  }

  /**
   * A well-formed tag that no revision can equal: keep 404 precedence for a missing product,
   * otherwise the precondition fails (412).
   */
  private Mono<ProductView> unmatchableRevision(String productCode) {
    return getProductUseCase
        .execute(new GetProductQuery(productCode))
        .then(Mono.error(() -> new ProductConcurrentModificationException(productCode)));
  }
}
