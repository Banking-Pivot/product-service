package com.banking.productservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.application.port.in.CreateProductUseCase;
import com.banking.productservice.application.port.in.GetProductUseCase;
import com.banking.productservice.application.port.in.ListProductsUseCase;
import com.banking.productservice.application.port.in.RetireProductUseCase;
import com.banking.productservice.application.port.in.UpdateProductUseCase;
import com.banking.productservice.support.ProductIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class ProductServiceApplicationTests extends ProductIntegrationTest {

  @Autowired private ApplicationContext context;

  /** The context starts and wires exactly one handler behind every inbound port. */
  @Test
  void contextLoads() {
    assertThat(context.getBeanNamesForType(CreateProductUseCase.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(GetProductUseCase.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(ListProductsUseCase.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(UpdateProductUseCase.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(RetireProductUseCase.class)).hasSize(1);
  }
}
