package com.banking.productservice.application.handler.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.out.ProductReadPort;
import com.banking.productservice.application.query.ListProductsQuery;
import com.banking.productservice.domain.model.enums.ProductFamily;
import com.banking.productservice.domain.model.enums.ProductStatus;
import com.banking.productservice.support.Products;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListProductsHandlerTest {

  @Mock private ProductReadPort readPort;

  private ListProductsHandler handler;

  @BeforeEach
  void createHandler() {
    handler = new ListProductsHandler(readPort);
  }

  @Test
  void returnsThePageWithTheTotalsOfTheWholeFilter() {
    ListProductsQuery query =
        new ListProductsQuery(1, 2, ProductFamily.SAVINGS, ProductStatus.ACTIVE);
    ProductView third = ProductView.from(Products.savings("SAVINGS_C"));
    when(readPort.findPage(query)).thenReturn(Flux.just(third));
    when(readPort.count(query)).thenReturn(Mono.just(3L));

    StepVerifier.create(handler.execute(query))
        .assertNext(
            page -> {
              assertThat(page.items()).containsExactly(third);
              assertThat(page.page()).isEqualTo(1);
              assertThat(page.size()).isEqualTo(2);
              assertThat(page.totalElements()).isEqualTo(3);
              assertThat(page.totalPages()).isEqualTo(2);
            })
        .verifyComplete();
  }

  @Test
  void emptyCatalogYieldsAnEmptyPageWithZeroPages() {
    ListProductsQuery query = new ListProductsQuery(0, 20, null, null);
    when(readPort.findPage(query)).thenReturn(Flux.empty());
    when(readPort.count(query)).thenReturn(Mono.just(0L));

    StepVerifier.create(handler.execute(query))
        .assertNext(
            page -> {
              assertThat(page.items()).isEmpty();
              assertThat(page.totalElements()).isZero();
              assertThat(page.totalPages()).isZero();
            })
        .verifyComplete();
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "10001,20", "0,0", "0,101"})
  void pageAndSizeOutOfBoundsAreRejected(int page, int size) {
    assertThatThrownBy(() -> new ListProductsQuery(page, size, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void offsetDoesNotOverflowOnTheLastAllowedPage() {
    assertThat(new ListProductsQuery(10_000, 100, null, null).offset()).isEqualTo(1_000_000L);
  }
}
