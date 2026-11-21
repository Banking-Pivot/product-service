package com.banking.productservice.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.infrastructure.adapter.out.mongodb.ProductDocument;
import com.banking.productservice.infrastructure.adapter.out.redis.RedisProductCacheAdapter;
import com.banking.productservice.support.ProductIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

/** GET /api/v1/products/{productCode} over HTTP against MongoDB, read through the Redis cache. */
class GetProductApiTest extends ProductIntegrationTest {

  @Test
  void returnsTheProductWithItsETag() {
    createProduct(checkingBody("CHECKING"));

    webTestClient
        .get()
        .uri("/api/v1/products/CHECKING")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"0\"")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CHECKING")
        .jsonPath("$.family")
        .isEqualTo("CHECKING")
        .jsonPath("$.status")
        .isEqualTo("ACTIVE")
        .jsonPath("$.eligibility.customerTypes[0]")
        .isEqualTo("PERSONAL")
        .jsonPath("$.eligibility.customerTypes[1]")
        .isEqualTo("BUSINESS")
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(20)
        .jsonPath("$.terms.movementFee.USD")
        .isEqualTo(0.3)
        .jsonPath("$.terms.monthlyMaintenanceFee.PEN")
        .isEqualTo(12.0)
        .jsonPath("$.terms.monthlyMovementLimit")
        .doesNotExist()
        .jsonPath("$.createdAt")
        .exists()
        .jsonPath("$.updatedAt")
        .exists();
  }

  @Test
  void termsThatDoNotApplyAreOmittedInsteadOfNull() {
    createProduct(savingsBody("SAVINGS"));

    String body =
        new String(
            webTestClient
                .get()
                .uri("/api/v1/products/SAVINGS")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent());

    assertThat(body)
        .contains("monthlyMovementLimit")
        .doesNotContain(
            "null",
            "requiredProfile",
            "monthlyMaintenanceFee",
            "minimumDailyAverageBalance",
            "operationDayOfMonth");
  }

  @Test
  void firstReadCachesTheProductForTheConfiguredTime() {
    createProduct(savingsBody("SAVINGS"));
    assertThat(cached("SAVINGS")).isNull();

    webTestClient.get().uri("/api/v1/products/SAVINGS").exchange().expectStatus().isOk();

    assertThat(cached("SAVINGS")).contains("\"code\":\"SAVINGS\"").contains("\"version\":0");
    Duration timeToLive = redis.getExpire(RedisProductCacheAdapter.KEY_PREFIX + "SAVINGS").block();
    assertThat(timeToLive).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(10));
  }

  @Test
  void cachedProductIsServedWithoutReadingMongo() {
    createProduct(savingsBody("SAVINGS"));
    webTestClient.get().uri("/api/v1/products/SAVINGS").exchange().expectStatus().isOk();

    // Changed behind the service's back: only a read from MongoDB would see it.
    mongoTemplate
        .updateFirst(
            Query.query(Criteria.where("_id").is("SAVINGS")),
            new Update().set("name", "Changed in MongoDB"),
            ProductDocument.COLLECTION)
        .block();

    webTestClient
        .get()
        .uri("/api/v1/products/SAVINGS")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"0\"")
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Savings account")
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(2.5)
        .jsonPath("$.terms.monthlyMaintenanceFee")
        .doesNotExist();
  }

  @Test
  void cachedAndStoredProductsAreTheSameRepresentation() {
    createProduct(fixedTermBody("FIXED_TERM"));

    byte[] fromMongo =
        webTestClient
            .get()
            .uri("/api/v1/products/FIXED_TERM")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBodyContent();
    byte[] fromCache =
        webTestClient
            .get()
            .uri("/api/v1/products/FIXED_TERM")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBodyContent();

    assertThat(cached("FIXED_TERM")).isNotNull();
    assertThat(new String(fromCache)).isEqualTo(new String(fromMongo));
  }

  @Test
  void cachedValueThisVersionCannotReadIsIgnoredAndReplaced() {
    createProduct(savingsBody("SAVINGS"));
    redis
        .opsForValue()
        .set(RedisProductCacheAdapter.KEY_PREFIX + "SAVINGS", "{\"not\":\"a product\"}")
        .block();

    webTestClient
        .get()
        .uri("/api/v1/products/SAVINGS")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Savings account");

    assertThat(cached("SAVINGS")).contains("\"code\":\"SAVINGS\"");
  }

  @Test
  void unknownProductReturns404AndIsNotCached() {
    webTestClient
        .get()
        .uri("/api/v1/products/UNKNOWN")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_NOT_FOUND")
        .jsonPath("$.message")
        .isEqualTo("The product does not exist.")
        .jsonPath("$.path")
        .isEqualTo("/api/v1/products/UNKNOWN")
        .jsonPath("$.timestamp")
        .exists();

    assertThat(cached("UNKNOWN")).isNull();
  }

  @Test
  void retiredProductIsStillReadable() {
    Created created = createProduct(savingsBody("SAVINGS"));
    webTestClient
        .delete()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", created.etag())
        .exchange()
        .expectStatus()
        .isNoContent();

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
        .isEqualTo("RETIRED")
        .jsonPath("$.terms.monthlyMovementLimit")
        .isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"savings", "S", "1SAVINGS", "VIP-SAVINGS"})
  void codeThatCannotExistReturns400(String code) {
    webTestClient
        .get()
        .uri("/api/v1/products/{code}", code)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");
  }
}
