package com.banking.productservice.infrastructure.config;

import com.banking.productservice.application.handler.command.CreateProductHandler;
import com.banking.productservice.application.handler.command.RetireProductHandler;
import com.banking.productservice.application.handler.command.UpdateProductHandler;
import com.banking.productservice.application.handler.query.GetProductHandler;
import com.banking.productservice.application.handler.query.ListProductsHandler;
import com.banking.productservice.application.port.in.CreateProductUseCase;
import com.banking.productservice.application.port.in.GetProductUseCase;
import com.banking.productservice.application.port.in.ListProductsUseCase;
import com.banking.productservice.application.port.in.RetireProductUseCase;
import com.banking.productservice.application.port.in.UpdateProductUseCase;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.application.port.out.ProductReadPort;
import com.banking.productservice.application.port.out.ProductWritePort;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the application handlers without putting Spring annotations on them. Command handlers
 * depend on the write port and query handlers on the read port; the cache is read by the product
 * query and cleared by the commands that change a product.
 */
@Configuration
@EnableConfigurationProperties({ProductCacheProperties.class, ProductCatalogProperties.class})
public class ProductBeanConfiguration {

  @Bean
  public Clock productClock() {
    return Clock.systemUTC();
  }

  @Bean
  public CreateProductUseCase createProductUseCase(ProductWritePort writePort, Clock clock) {
    return new CreateProductHandler(writePort, clock);
  }

  @Bean
  public UpdateProductUseCase updateProductUseCase(
      ProductWritePort writePort, ProductCachePort cache, Clock clock) {
    return new UpdateProductHandler(writePort, cache, clock);
  }

  @Bean
  public RetireProductUseCase retireProductUseCase(
      ProductWritePort writePort, ProductCachePort cache, Clock clock) {
    return new RetireProductHandler(writePort, cache, clock);
  }

  @Bean
  public GetProductUseCase getProductUseCase(ProductReadPort readPort, ProductCachePort cache) {
    return new GetProductHandler(readPort, cache);
  }

  @Bean
  public ListProductsUseCase listProductsUseCase(ProductReadPort readPort) {
    return new ListProductsHandler(readPort);
  }
}
