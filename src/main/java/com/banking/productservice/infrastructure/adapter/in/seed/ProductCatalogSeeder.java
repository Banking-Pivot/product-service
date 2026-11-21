package com.banking.productservice.infrastructure.adapter.in.seed;

import com.banking.productservice.application.exception.DuplicateProductCodeException;
import com.banking.productservice.application.port.in.CreateProductUseCase;
import com.banking.productservice.infrastructure.adapter.in.rest.ProductRestMapper;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.CreateProductRequest;
import com.banking.productservice.infrastructure.config.ProductCatalogProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adds the bank's default products to an empty or incomplete catalog when the service starts, so a
 * new environment can open accounts without a manual load.
 *
 * <p>The products are in {@code catalog/default-products.json}, written as the body of {@code POST
 * /api/v1/products}, and go through the same use case as that request, so they obey the same rules.
 * Only missing codes are added: a product that already exists is left as it is, edited or retired,
 * because the catalog and not this file is the source of truth once the service has run.
 */
@Slf4j
@Component
public class ProductCatalogSeeder implements ApplicationRunner {

  static final String DEFAULT_CATALOG = "catalog/default-products.json";

  private final CreateProductUseCase createProductUseCase;
  private final ProductRestMapper mapper;
  private final JsonMapper json;
  private final ProductCatalogProperties properties;

  /**
   * Receives the creation use case, the REST mapper and JSON mapper that read the default products,
   * and the catalog settings that switch the seeding on or off.
   */
  public ProductCatalogSeeder(
      CreateProductUseCase createProductUseCase,
      ProductRestMapper mapper,
      JsonMapper json,
      ProductCatalogProperties properties) {
    this.createProductUseCase = createProductUseCase;
    this.mapper = mapper;
    this.json = json;
    this.properties = properties;
  }

  /** Runs once the application is ready to start; a failure here stops the startup. */
  @Override
  public void run(ApplicationArguments args) {
    if (!properties.seed()) {
      return;
    }
    int added = seed().block();
    log.info("Default product catalog checked. added={}", added);
  }

  /** Adds the default products that are missing and emits how many were added. */
  public Mono<Integer> seed() {
    return Flux.defer(() -> Flux.fromIterable(defaultProducts()))
        .concatMap(
            request ->
                createProductUseCase
                    .execute(mapper.toCommand(request))
                    .thenReturn(1)
                    // Already in the catalog, or another instance added it first.
                    .onErrorReturn(DuplicateProductCodeException.class, 0))
        .reduce(0, Integer::sum);
  }

  private List<CreateProductRequest> defaultProducts() {
    try (InputStream catalog = new ClassPathResource(DEFAULT_CATALOG).getInputStream()) {
      return List.of(json.readValue(catalog, CreateProductRequest[].class));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
