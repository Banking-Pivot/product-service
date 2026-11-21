package com.banking.productservice.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductPageResponse;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductResponse;
import com.banking.productservice.support.ProductIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** GET /api/v1/products over HTTP against MongoDB. */
class ListProductsApiTest extends ProductIntegrationTest {

  @Test
  void usesDefaultsAndOrdersByCodeAscending() {
    createProduct(savingsBody("SAVINGS"));
    createProduct(fixedTermBody("FIXED_TERM"));
    createProduct(checkingBody("CHECKING"));

    ProductPageResponse page = list("/api/v1/products");

    assertThat(page.getPage()).isZero();
    assertThat(page.getSize()).isEqualTo(20);
    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getTotalPages()).isEqualTo(1);
    assertThat(codesOf(page)).containsExactly("CHECKING", "FIXED_TERM", "SAVINGS");
    assertThat(page.getItems().get(0).getTerms().getMonthlyMaintenanceFee().getPEN())
        .isEqualByComparingTo("12.00");
  }

  @Test
  void walksPagesInStableOrderAndReturnsEmptyItemsPastTheLastPage() {
    for (String code : List.of("SAVINGS_E", "SAVINGS_C", "SAVINGS_A", "SAVINGS_D", "SAVINGS_B")) {
      createProduct(savingsBody(code));
    }

    List<String> walked = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      ProductPageResponse response = list("/api/v1/products?page=" + page + "&size=2");
      assertThat(response.getTotalElements()).isEqualTo(5);
      assertThat(response.getTotalPages()).isEqualTo(3);
      walked.addAll(codesOf(response));
    }

    assertThat(walked)
        .containsExactly("SAVINGS_A", "SAVINGS_B", "SAVINGS_C", "SAVINGS_D", "SAVINGS_E");
    assertThat(list("/api/v1/products?page=3&size=2").getItems()).isEmpty();
  }

  @Test
  void filtersByFamily() {
    createProduct(savingsBody("SAVINGS"));
    createProduct(savingsBody("VIP_SAVINGS"));
    createProduct(checkingBody("CHECKING"));

    ProductPageResponse page = list("/api/v1/products?family=SAVINGS");

    assertThat(codesOf(page)).containsExactly("SAVINGS", "VIP_SAVINGS");
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void filtersByStatusAndListsRetiredProductsWhenNoStatusIsGiven() {
    createProduct(checkingBody("CHECKING"));
    Created retired = createProduct(savingsBody("SAVINGS"));
    webTestClient
        .delete()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", retired.etag())
        .exchange()
        .expectStatus()
        .isNoContent();

    assertThat(codesOf(list("/api/v1/products?status=ACTIVE"))).containsExactly("CHECKING");
    assertThat(codesOf(list("/api/v1/products?status=RETIRED"))).containsExactly("SAVINGS");
    assertThat(codesOf(list("/api/v1/products"))).containsExactly("CHECKING", "SAVINGS");
    assertThat(codesOf(list("/api/v1/products?family=SAVINGS&status=ACTIVE"))).isEmpty();
  }

  @Test
  void emptyCatalogReturnsAnEmptyPage() {
    ProductPageResponse page = list("/api/v1/products");

    assertThat(page.getItems()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
    assertThat(page.getTotalPages()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/products?page=-1",
        "/api/v1/products?page=10001",
        "/api/v1/products?size=0",
        "/api/v1/products?size=101",
        "/api/v1/products?page=abc",
        "/api/v1/products?family=CREDIT",
        "/api/v1/products?status=DELETED"
      })
  void invalidParametersReturn400(String uri) {
    webTestClient
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");
  }

  private ProductPageResponse list(String uri) {
    return webTestClient
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ProductPageResponse.class)
        .returnResult()
        .getResponseBody();
  }

  private static List<String> codesOf(ProductPageResponse page) {
    return page.getItems().stream().map(ProductResponse::getCode).toList();
  }
}
