package com.banking.productservice.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.support.ProductIntegrationTest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** PUT /api/v1/products/{productCode} over HTTP against MongoDB and the Redis cache. */
class UpdateProductApiTest extends ProductIntegrationTest {

  @Test
  void replacesNameEligibilityAndTermsAndReturnsTheNewETag() {
    Created created = createProduct(savingsBody("SAVINGS"));
    Document before = stored("SAVINGS");

    put("SAVINGS", created.etag(), vipSavingsUpdate())
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"1\"")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("SAVINGS")
        .jsonPath("$.family")
        .isEqualTo("SAVINGS")
        .jsonPath("$.status")
        .isEqualTo("ACTIVE")
        .jsonPath("$.name")
        .isEqualTo("Premium savings")
        .jsonPath("$.eligibility.requiredProfile")
        .isEqualTo("VIP")
        .jsonPath("$.eligibility.requiresCreditCard")
        .isEqualTo(true)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(10)
        .jsonPath("$.terms.monthlyMovementLimit")
        .isEqualTo(20)
        .jsonPath("$.terms.minimumDailyAverageBalance.PEN")
        .isEqualTo(1000.0);

    Document after = stored("SAVINGS");
    assertThat(after.get("version", Number.class).longValue()).isEqualTo(1);
    assertThat(after.getString("name")).isEqualTo("Premium savings");
    assertThat(after.getString("requiredProfile")).isEqualTo("VIP");
    assertThat(after.get("createdAt", Date.class)).isEqualTo(before.get("createdAt", Date.class));
    assertThat(after.get("updatedAt", Date.class)).isAfter(before.get("updatedAt", Date.class));
    assertThat(after.getString("family")).isEqualTo("SAVINGS");
  }

  @Test
  void termThatStopsApplyingIsRemovedFromTheStoredProduct() {
    Created created = createProduct(savingsBody("SAVINGS"));
    String updatedETag =
        put("SAVINGS", created.etag(), vipSavingsUpdate())
            .expectStatus()
            .isOk()
            .returnResult(Void.class)
            .getResponseHeaders()
            .getETag();
    assertThat(stored("SAVINGS")).containsKeys("requiredProfile", "minimumDailyAverageBalance");

    put("SAVINGS", updatedETag, updateBody(savingsBody("SAVINGS")))
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"2\"")
        .expectBody()
        .jsonPath("$.eligibility.requiredProfile")
        .doesNotExist()
        .jsonPath("$.terms.minimumDailyAverageBalance")
        .doesNotExist();

    assertThat(stored("SAVINGS"))
        .doesNotContainKeys("requiredProfile", "minimumDailyAverageBalance");
  }

  @Test
  void updateRemovesTheProductFromTheCacheSoTheNextReadIsCurrent() {
    Created created = createProduct(savingsBody("SAVINGS"));
    webTestClient.get().uri("/api/v1/products/SAVINGS").exchange().expectStatus().isOk();
    assertThat(cached("SAVINGS")).isNotNull();

    put("SAVINGS", created.etag(), vipSavingsUpdate()).expectStatus().isOk();

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
        .jsonPath("$.name")
        .isEqualTo("Premium savings")
        .jsonPath("$.terms.monthlyMovementLimit")
        .isEqualTo(20);
    assertThat(cached("SAVINGS")).contains("\"version\":1");
  }

  @Test
  void identicalValuesWithMatchingRevisionKeepTheRevision() {
    Created created = createProduct(savingsBody("SAVINGS"));
    Document before = stored("SAVINGS");
    Map<String, Object> same = updateBody(savingsBody("SAVINGS"));
    same.put("name", " Savings account ");

    put("SAVINGS", created.etag(), same)
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"0\"")
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Savings account");

    assertThat(stored("SAVINGS")).isEqualTo(before);
  }

  @Test
  void staleETagReturns412AndDoesNotOverwrite() {
    createProduct(savingsBody("SAVINGS"));
    put("SAVINGS", "\"0\"", vipSavingsUpdate()).expectStatus().isOk();

    Map<String, Object> second = updateBody(savingsBody("SAVINGS"));
    second.put("name", "Second writer");
    put("SAVINGS", "\"0\"", second)
        .expectStatus()
        .isEqualTo(412)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_VERSION_CONFLICT");

    Document after = stored("SAVINGS");
    assertThat(after.getString("name")).isEqualTo("Premium savings");
    assertThat(after.get("version", Number.class).longValue()).isEqualTo(1);
  }

  @Test
  void missingIfMatchReturns428() {
    createProduct(savingsBody("SAVINGS"));

    webTestClient
        .put()
        .uri("/api/v1/products/SAVINGS")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(vipSavingsUpdate())
        .exchange()
        .expectStatus()
        .isEqualTo(428)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IF_MATCH_REQUIRED");

    assertThat(stored("SAVINGS").get("version", Number.class).longValue()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "W/\"0\"",
        "*",
        "0",
        "\"0\", \"1\"",
        "\"abc\"",
        "\"\"",
        "\"99999999999999999999\""
      })
  void malformedIfMatchReturns400(String ifMatch) {
    createProduct(savingsBody("SAVINGS"));

    put("SAVINGS", ifMatch, vipSavingsUpdate())
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_IF_MATCH");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"00\"", "\"9999999999999999999\""})
  void wellFormedTagThatCanNeverMatchReturns412(String ifMatch) {
    createProduct(savingsBody("SAVINGS"));

    put("SAVINGS", ifMatch, vipSavingsUpdate())
        .expectStatus()
        .isEqualTo(412)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_VERSION_CONFLICT");
  }

  @Test
  void retiredProductReturns409() {
    Created created = createProduct(savingsBody("SAVINGS"));
    retire("SAVINGS", created.etag());

    put("SAVINGS", "\"1\"", vipSavingsUpdate())
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_RETIRED");
  }

  @Test
  void retiredProductWithStaleETagReturns412First() {
    Created created = createProduct(savingsBody("SAVINGS"));
    retire("SAVINGS", created.etag());

    put("SAVINGS", "\"0\"", vipSavingsUpdate()).expectStatus().isEqualTo(412);
  }

  @Test
  void termsAgainstTheFamilyReturn422AndKeepTheProduct() {
    Created created = createProduct(savingsBody("SAVINGS"));
    Document before = stored("SAVINGS");

    put("SAVINGS", created.etag(), updateBody(checkingBody("SAVINGS")))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_RULE_VIOLATION")
        .jsonPath("$.message")
        .isEqualTo("A SAVINGS product requires a monthly movement limit.");

    assertThat(stored("SAVINGS")).isEqualTo(before);
  }

  @Test
  void codeAndFamilyCannotBeSentInAnUpdate() {
    Created created = createProduct(savingsBody("SAVINGS"));

    for (Map<String, Object> body :
        List.of(
            with(vipSavingsUpdate(), "family", "CHECKING"),
            with(vipSavingsUpdate(), "code", "OTHER"),
            with(vipSavingsUpdate(), "status", "RETIRED"))) {
      put("SAVINGS", created.etag(), body)
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("INVALID_REQUEST");
    }

    assertThat(stored("SAVINGS").get("version", Number.class).longValue()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "eligibility", "terms"})
  void missingRequiredFieldReturns400(String field) {
    Created created = createProduct(savingsBody("SAVINGS"));
    Map<String, Object> body = vipSavingsUpdate();
    body.remove(field);

    put("SAVINGS", created.etag(), body)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void unknownProductReturns404() {
    put("UNKNOWN", "\"0\"", vipSavingsUpdate())
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_NOT_FOUND");
  }

  @Test
  void unknownProductWithUnmatchableTagStillReturns404() {
    put("UNKNOWN", "\"00\"", vipSavingsUpdate()).expectStatus().isNotFound();
  }

  @Test
  void nonJsonContentTypeReturns415() {
    createProduct(savingsBody("SAVINGS"));

    webTestClient
        .put()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", "\"0\"")
        .contentType(MediaType.TEXT_PLAIN)
        .bodyValue("name=Savings")
        .exchange()
        .expectStatus()
        .isEqualTo(415)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNSUPPORTED_MEDIA_TYPE");
  }

  /** Savings for VIP customers with a credit card, a higher limit and an average balance. */
  private static Map<String, Object> vipSavingsUpdate() {
    Map<String, Object> terms = baseTerms("1000.00", "300.00", 10, "1.50", "0.50");
    terms.put("monthlyMovementLimit", 20);
    terms.put("minimumDailyAverageBalance", amounts("1000.00", "300.00"));
    return updateBody(
        productBody(
            "SAVINGS",
            "Premium savings",
            "SAVINGS",
            eligibility(List.of("PERSONAL"), "VIP", true),
            terms));
  }

  private static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
    body.put(field, value);
    return body;
  }

  private WebTestClient.ResponseSpec put(String code, String ifMatch, Map<String, Object> body) {
    return webTestClient
        .put()
        .uri("/api/v1/products/{code}", code)
        .header("If-Match", ifMatch)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private void retire(String code, String ifMatch) {
    webTestClient
        .delete()
        .uri("/api/v1/products/{code}", code)
        .header("If-Match", ifMatch)
        .exchange()
        .expectStatus()
        .isNoContent();
  }
}
