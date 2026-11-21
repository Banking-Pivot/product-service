package com.banking.productservice.application.handler.query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banking.productservice.application.exception.ProductNotFoundException;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.application.port.out.ProductReadPort;
import com.banking.productservice.application.query.GetProductQuery;
import com.banking.productservice.support.Products;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetProductHandlerTest {

  private static final ProductView SAVINGS = ProductView.from(Products.savings("SAVINGS"));

  @Mock private ProductReadPort readPort;

  @Mock private ProductCachePort cache;

  private GetProductHandler handler;

  @BeforeEach
  void createHandler() {
    handler = new GetProductHandler(readPort, cache);
  }

  @Test
  void cachedProductIsReturnedWithoutReadingTheStore() {
    when(cache.find("SAVINGS")).thenReturn(Mono.just(SAVINGS));

    StepVerifier.create(handler.execute(new GetProductQuery("SAVINGS")))
        .expectNext(SAVINGS)
        .verifyComplete();

    verifyNoInteractions(readPort);
    verify(cache, never()).store(any());
  }

  @Test
  void productThatIsNotCachedIsReadFromTheStoreAndCached() {
    when(cache.find("SAVINGS")).thenReturn(Mono.empty());
    when(readPort.findByCode("SAVINGS")).thenReturn(Mono.just(SAVINGS));
    when(cache.store(SAVINGS)).thenReturn(Mono.empty());

    StepVerifier.create(handler.execute(new GetProductQuery("SAVINGS")))
        .expectNext(SAVINGS)
        .verifyComplete();

    verify(cache).store(SAVINGS);
  }

  @Test
  void unknownProductIsNotFoundAndIsNotCached() {
    when(cache.find("UNKNOWN")).thenReturn(Mono.empty());
    when(readPort.findByCode("UNKNOWN")).thenReturn(Mono.empty());

    StepVerifier.create(handler.execute(new GetProductQuery("UNKNOWN")))
        .expectError(ProductNotFoundException.class)
        .verify();

    verify(cache, never()).store(any());
  }
}
