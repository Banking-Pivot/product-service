package com.banking.productservice.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.support.ProductIntegrationTest;
import java.util.Date;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** DELETE /api/v1/products/{productCode} over HTTP against MongoDB and the Redis cache. */
class RetireProductApiTest extends ProductIntegrationTest {

  @Test
  void retiresTheProductLogicallyAndKeepsItsRecord() {
    Created created = createProduct(savingsBody("SAVINGS"));
    Document before = stored("SAVINGS");

    delete("SAVINGS", created.etag()).expectStatus().isNoContent().expectBody().isEmpty();

    Document after = stored("SAVINGS");
    assertThat(storedCount()).isEqualTo(1);
    assertThat(after.getString("status")).isEqualTo("RETIRED");
    assertThat(after.get("version", Number.class).longValue()).isEqualTo(1);
    assertThat(after.get("updatedAt", Date.class)).isAfter(before.get("updatedAt", Date.class));
    assertThat(after.get("createdAt", Date.class)).isEqualTo(before.get("createdAt", Date.class));
    assertThat(after).containsEntry("movementFee", before.get("movementFee"));
    assertThat(after.getInteger("monthlyMovementLimit")).isEqualTo(10);
  }

  @Test
  void retirementRemovesTheProductFromTheCacheSoTheNextReadIsCurrent() {
    Created created = createProduct(savingsBody("SAVINGS"));
    webTestClient.get().uri("/api/v1/products/SAVINGS").exchange().expectStatus().isOk();
    assertThat(cached("SAVINGS")).contains("\"status\":\"ACTIVE\"");

    delete("SAVINGS", created.etag()).expectStatus().isNoContent();

    assertThat(cached("SAVINGS")).isNull();
    webTestClient
        .get()
        .uri("/api/v1/products/SAVINGS")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"1\"")
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("RETIRED");
  }

  @Test
  void repeatingTheRetirementWithTheCurrentRevisionIsANoOp() {
    Created created = createProduct(savingsBody("SAVINGS"));
    delete("SAVINGS", created.etag()).expectStatus().isNoContent();
    Document retired = stored("SAVINGS");

    delete("SAVINGS", "\"1\"").expectStatus().isNoContent();

    assertThat(stored("SAVINGS")).isEqualTo(retired);
  }

  @Test
  void repeatingTheRetirementWithTheOldRevisionReturns412() {
    Created created = createProduct(savingsBody("SAVINGS"));
    delete("SAVINGS", created.etag()).expectStatus().isNoContent();

    delete("SAVINGS", created.etag())
        .expectStatus()
        .isEqualTo(412)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_VERSION_CONFLICT");
  }

  @Test
  void staleETagReturns412AndKeepsTheProductActive() {
    createProduct(savingsBody("SAVINGS"));

    delete("SAVINGS", "\"5\"")
        .expectStatus()
        .isEqualTo(412)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_VERSION_CONFLICT");

    assertThat(stored("SAVINGS").getString("status")).isEqualTo("ACTIVE");
  }

  @Test
  void missingIfMatchReturns428() {
    createProduct(savingsBody("SAVINGS"));

    webTestClient
        .delete()
        .uri("/api/v1/products/SAVINGS")
        .exchange()
        .expectStatus()
        .isEqualTo(428)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IF_MATCH_REQUIRED");

    assertThat(stored("SAVINGS").getString("status")).isEqualTo("ACTIVE");
  }

  @ParameterizedTest
  @ValueSource(strings = {"W/\"0\"", "*", "0", "\"abc\""})
  void malformedIfMatchReturns400(String ifMatch) {
    createProduct(savingsBody("SAVINGS"));

    delete("SAVINGS", ifMatch)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_IF_MATCH");
  }

  @Test
  void wellFormedTagThatCanNeverMatchReturns412() {
    createProduct(savingsBody("SAVINGS"));

    delete("SAVINGS", "\"00\"").expectStatus().isEqualTo(412);
  }

  @Test
  void unknownProductReturns404() {
    delete("UNKNOWN", "\"0\"")
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_NOT_FOUND");
  }

  private WebTestClient.ResponseSpec delete(String code, String ifMatch) {
    return webTestClient
        .delete()
        .uri("/api/v1/products/{code}", code)
        .header("If-Match", ifMatch)
        .exchange();
  }
}
