package com.banking.productservice.application.handler.command;

import static com.banking.productservice.support.Products.T0;
import static com.banking.productservice.support.Products.amounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banking.productservice.application.command.UpdateProductCommand;
import com.banking.productservice.application.exception.ProductConcurrentModificationException;
import com.banking.productservice.application.exception.ProductNotFoundException;
import com.banking.productservice.application.exception.RetiredProductException;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.Product;
import com.banking.productservice.domain.model.vo.ProductTerms;
import com.banking.productservice.support.Products;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UpdateProductHandlerTest {

  private static final Instant T1 = T0.plusSeconds(60);
  private static final ProductTerms NEW_TERMS =
      new ProductTerms(amounts("0.00", "0.00"), 8, amounts("2.00", "0.60"), 15, null, null, null);

  @Mock private ProductWritePort writePort;

  @Mock private ProductCachePort cache;

  private UpdateProductHandler handler;

  @BeforeEach
  void createHandler() {
    handler = new UpdateProductHandler(writePort, cache, Clock.fixed(T1, ZoneOffset.UTC));
  }

  @Test
  void writesWithTheClientRevisionThenRemovesTheProductFromTheCache() {
    when(writePort.findByCode("SAVINGS")).thenReturn(Mono.just(Products.savings("SAVINGS")));
    when(writePort.update(any(), eq(0L)))
        .thenAnswer(call -> Mono.just(Products.atRevision(call.getArgument(0, Product.class), 1)));
    when(cache.evict("SAVINGS")).thenReturn(Mono.empty());

    StepVerifier.create(handler.execute(command("Everyday savings", NEW_TERMS, 0)))
        .assertNext(
            view -> {
              assertThat(view.version()).isEqualTo(1);
              assertThat(view.name()).isEqualTo("Everyday savings");
              assertThat(view.terms()).isEqualTo(NEW_TERMS);
              assertThat(view.updatedAt()).isEqualTo(T1);
              assertThat(view.createdAt()).isEqualTo(T0);
            })
        .verifyComplete();

    InOrder order = inOrder(writePort, cache);
    order.verify(writePort).update(any(), eq(0L));
    order.verify(cache).evict("SAVINGS");
  }

  @Test
  void identicalValuesSkipTheWriteAndKeepTheRevisionAndTheCache() {
    when(writePort.findByCode("SAVINGS")).thenReturn(Mono.just(Products.savings("SAVINGS")));

    StepVerifier.create(handler.execute(command(" Savings account ", Products.savingsTerms(), 0)))
        .assertNext(
            view -> {
              assertThat(view.version()).isZero();
              assertThat(view.updatedAt()).isEqualTo(T0);
            })
        .verifyComplete();

    verify(writePort, never()).update(any(), anyLong());
    verifyNoInteractions(cache);
  }

  @Test
  void staleRevisionFailsBeforeWriting() {
    when(writePort.findByCode("SAVINGS"))
        .thenReturn(Mono.just(Products.atRevision(Products.savings("SAVINGS"), 3)));

    StepVerifier.create(handler.execute(command("Everyday savings", NEW_TERMS, 2)))
        .expectError(ProductConcurrentModificationException.class)
        .verify();

    verify(writePort, never()).update(any(), anyLong());
    verifyNoInteractions(cache);
  }

  @Test
  void writerThatWinsBetweenReadAndWriteMakesThisOneFailWithoutTouchingTheCache() {
    when(writePort.findByCode("SAVINGS")).thenReturn(Mono.just(Products.savings("SAVINGS")));
    // The store matched nothing: the revision moved after this handler read it.
    when(writePort.update(any(), eq(0L))).thenReturn(Mono.empty());

    StepVerifier.create(handler.execute(command("Late writer", NEW_TERMS, 0)))
        .expectError(ProductConcurrentModificationException.class)
        .verify();

    verifyNoInteractions(cache);
  }

  @Test
  void retiredProductCannotBeUpdated() {
    when(writePort.findByCode("SAVINGS")).thenReturn(Mono.just(retired(1)));

    StepVerifier.create(handler.execute(command("Everyday savings", NEW_TERMS, 1)))
        .expectError(RetiredProductException.class)
        .verify();

    verify(writePort, never()).update(any(), anyLong());
  }

  @Test
  void staleRevisionIsReportedBeforeTheRetirement() {
    when(writePort.findByCode("SAVINGS")).thenReturn(Mono.just(retired(1)));

    StepVerifier.create(handler.execute(command("Everyday savings", NEW_TERMS, 0)))
        .expectError(ProductConcurrentModificationException.class)
        .verify();
  }

  @Test
  void termsAgainstTheFamilyAreRejectedAndNothingIsWritten() {
    when(writePort.findByCode("SAVINGS")).thenReturn(Mono.just(Products.savings("SAVINGS")));

    StepVerifier.create(handler.execute(command("Everyday savings", Products.checkingTerms(), 0)))
        .expectError(InvalidProductException.class)
        .verify();

    verify(writePort, never()).update(any(), anyLong());
    verifyNoInteractions(cache);
  }

  @Test
  void unknownProductIsNotFound() {
    when(writePort.findByCode("UNKNOWN")).thenReturn(Mono.empty());

    StepVerifier.create(
            handler.execute(
                new UpdateProductCommand("UNKNOWN", "Name", Products.personal(), NEW_TERMS, 0)))
        .expectError(ProductNotFoundException.class)
        .verify();
  }

  private static UpdateProductCommand command(
      String name, ProductTerms terms, long expectedVersion) {
    return new UpdateProductCommand("SAVINGS", name, Products.personal(), terms, expectedVersion);
  }

  private static Product retired(long version) {
    Product product = Products.savings("SAVINGS");
    product.retire(T0);
    return Products.atRevision(product, version);
  }
}
