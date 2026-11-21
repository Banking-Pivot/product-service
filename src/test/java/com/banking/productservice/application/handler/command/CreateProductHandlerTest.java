package com.banking.productservice.application.handler.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banking.productservice.application.command.CreateProductCommand;
import com.banking.productservice.application.exception.DuplicateProductCodeException;
import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.Product;
import com.banking.productservice.domain.model.ProductFamily;
import com.banking.productservice.domain.model.ProductStatus;
import com.banking.productservice.support.Products;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreateProductHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-05T15:00:00.123456789Z");

  @Mock private ProductWritePort writePort;

  private CreateProductHandler handler;

  @BeforeEach
  void createHandler() {
    handler = new CreateProductHandler(writePort, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void insertsAnActiveProductDatedToTheMillisecond() {
    when(writePort.insert(any())).thenAnswer(call -> Mono.just(call.getArgument(0, Product.class)));

    StepVerifier.create(handler.execute(savingsCommand("SAVINGS")))
        .assertNext(
            view -> {
              assertThat(view.code()).isEqualTo("SAVINGS");
              assertThat(view.family()).isEqualTo(ProductFamily.SAVINGS);
              assertThat(view.status()).isEqualTo(ProductStatus.ACTIVE);
              assertThat(view.version()).isZero();
              assertThat(view.createdAt()).isEqualTo(Instant.parse("2026-11-05T15:00:00.123Z"));
              assertThat(view.updatedAt()).isEqualTo(view.createdAt());
            })
        .verifyComplete();

    ArgumentCaptor<Product> inserted = ArgumentCaptor.forClass(Product.class);
    verify(writePort).insert(inserted.capture());
    assertThat(inserted.getValue().getTerms()).isEqualTo(Products.savingsTerms());
    assertThat(inserted.getValue().getEligibility()).isEqualTo(Products.personal());
  }

  @Test
  void ruleViolationIsEmittedAsAnErrorAndNothingIsInserted() {
    CreateProductCommand command =
        new CreateProductCommand(
            "SAVINGS",
            "Savings account",
            ProductFamily.SAVINGS,
            Products.personal(),
            Products.fixedTermTerms());

    StepVerifier.create(handler.execute(command))
        .expectError(InvalidProductException.class)
        .verify();

    verify(writePort, never()).insert(any());
  }

  @Test
  void codeAlreadyInTheCatalogIsReportedAsADuplicate() {
    when(writePort.insert(any()))
        .thenReturn(Mono.error(new DuplicateProductCodeException(new RuntimeException("E11000"))));

    StepVerifier.create(handler.execute(savingsCommand("SAVINGS")))
        .expectError(DuplicateProductCodeException.class)
        .verify();
  }

  private static CreateProductCommand savingsCommand(String code) {
    return new CreateProductCommand(
        code,
        "Savings account",
        ProductFamily.SAVINGS,
        Products.personal(),
        Products.savingsTerms());
  }
}
