package com.banking.productservice.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.productservice.application.model.ProductPage;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductResponse;
import com.banking.productservice.support.Products;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ServerWebInputException;

class ProductRestMapperTest {

  private final ProductRestMapper mapper = new ProductRestMapper();

  @Test
  void etagIsTheQuotedRevision() {
    assertThat(mapper.toEtag(0)).isEqualTo("\"0\"");
    assertThat(mapper.toExpectedVersion("\"42\"")).isEqualTo(OptionalLong.of(42));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"00\"", "\"042\"", "\"9223372036854775808\""})
  void wellFormedTagsThatNoRevisionCanEqualAreUnmatchable(String tag) {
    assertThat(mapper.toExpectedVersion(tag)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "W/\"1\"", "1", "\"1\",\"2\"", "\"-1\""})
  void malformedTagsAreRejected(String tag) {
    assertThatThrownBy(() -> mapper.toExpectedVersion(tag))
        .isInstanceOf(ServerWebInputException.class);
  }

  @Test
  void responseLeavesOutTheTermsThatDoNotApplyToTheProduct() {
    ProductResponse response = mapper.toResponse(ProductView.from(Products.savings("SAVINGS")));

    assertThat(response.getTerms().getMonthlyMovementLimit()).isEqualTo(10);
    assertThat(response.getTerms().getMovementFee().getPEN()).isEqualByComparingTo("2.50");
    assertThat(response.getTerms().getMonthlyMaintenanceFee()).isNull();
    assertThat(response.getTerms().getMinimumDailyAverageBalance()).isNull();
    assertThat(response.getTerms().getOperationDayOfMonth()).isNull();
    assertThat(response.getEligibility().getRequiredProfile()).isNull();
  }

  @Test
  void totalPagesIsTheCeilingAndZeroWhenEmpty() {
    assertThat(ProductPage.of(List.of(), 0, 20, 0).totalPages()).isZero();
    assertThat(ProductPage.of(List.of(), 0, 2, 4).totalPages()).isEqualTo(2);
    assertThat(ProductPage.of(List.of(), 0, 2, 5).totalPages()).isEqualTo(3);
  }
}
