package com.banking.productservice.infrastructure.adapter.in.rest;

import static com.banking.productservice.support.SteppingClockConfiguration.START;
import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.support.ProductIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** POST /api/v1/products over HTTP against MongoDB. */
class CreateProductApiTest extends ProductIntegrationTest {

  @Test
  void createsAnActiveProductWithLocationAndETag() {
    post(savingsBody("SAVINGS"))
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals("Location", "/api/v1/products/SAVINGS")
        .expectHeader()
        .valueEquals("ETag", "\"0\"")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("SAVINGS")
        .jsonPath("$.name")
        .isEqualTo("Savings account")
        .jsonPath("$.family")
        .isEqualTo("SAVINGS")
        .jsonPath("$.status")
        .isEqualTo("ACTIVE")
        .jsonPath("$.eligibility.customerTypes.length()")
        .isEqualTo(1)
        .jsonPath("$.eligibility.customerTypes[0]")
        .isEqualTo("PERSONAL")
        .jsonPath("$.eligibility.requiresCreditCard")
        .isEqualTo(false)
        .jsonPath("$.eligibility.requiredProfile")
        .doesNotExist()
        .jsonPath("$.terms.openingMinimum.PEN")
        .isEqualTo(0.0)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(5)
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(2.5)
        .jsonPath("$.terms.movementFee.USD")
        .isEqualTo(0.75)
        .jsonPath("$.terms.monthlyMovementLimit")
        .isEqualTo(10)
        .jsonPath("$.terms.monthlyMaintenanceFee")
        .doesNotExist()
        .jsonPath("$.terms.minimumDailyAverageBalance")
        .doesNotExist()
        .jsonPath("$.terms.operationDayOfMonth")
        .doesNotExist()
        .jsonPath("$.createdAt")
        .value(
            String.class, createdAt -> assertThat(Instant.parse(createdAt)).isAfterOrEqualTo(START))
        .jsonPath("$.version")
        .doesNotExist();

    Document stored = stored("SAVINGS");
    assertThat(stored.get("updatedAt", Date.class)).isEqualTo(stored.get("createdAt", Date.class));
  }

  @Test
  void storesTheCodeAsDocumentIdAndTheAmountsAsDecimals() {
    post(savingsBody("SAVINGS")).expectStatus().isCreated();

    Document stored = stored("SAVINGS");
    assertThat(stored.getString("_id")).isEqualTo("SAVINGS");
    assertThat(stored.getString("family")).isEqualTo("SAVINGS");
    assertThat(stored.getString("status")).isEqualTo("ACTIVE");
    assertThat(stored.getList("customerTypes", String.class)).containsExactly("PERSONAL");
    assertThat(stored.get("movementFee", Document.class).get("PEN"))
        .isEqualTo(new Decimal128(new BigDecimal("2.50")));
    assertThat(stored.get("movementFee", Document.class).get("USD"))
        .isEqualTo(new Decimal128(new BigDecimal("0.75")));
    assertThat(stored.getInteger("monthlyMovementLimit")).isEqualTo(10);
    assertThat(stored.get("version", Number.class).longValue()).isZero();
    assertThat(stored.get("createdAt", Date.class)).isAfterOrEqualTo(Date.from(START));
    // Terms that do not apply to the product are not stored at all.
    assertThat(stored)
        .doesNotContainKeys(
            "requiredProfile",
            "monthlyMaintenanceFee",
            "minimumDailyAverageBalance",
            "operationDayOfMonth");
  }

  @Test
  void createsTheFiveProductsOfTheBank() {
    Map<String, Object> vipTerms = baseTerms("1000.00", "300.00", 10, "1.50", "0.50");
    vipTerms.put("monthlyMovementLimit", 20);
    vipTerms.put("minimumDailyAverageBalance", amounts("1000.00", "300.00"));

    post(savingsBody("SAVINGS")).expectStatus().isCreated();
    post(productBody(
            "VIP_SAVINGS",
            "VIP savings account",
            "SAVINGS",
            eligibility(List.of("PERSONAL"), "VIP", true),
            vipTerms))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.eligibility.requiredProfile")
        .isEqualTo("VIP")
        .jsonPath("$.eligibility.requiresCreditCard")
        .isEqualTo(true)
        .jsonPath("$.terms.minimumDailyAverageBalance.USD")
        .isEqualTo(300.0);
    post(checkingBody("CHECKING"))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.eligibility.customerTypes.length()")
        .isEqualTo(2)
        .jsonPath("$.terms.monthlyMaintenanceFee.PEN")
        .isEqualTo(12.0)
        .jsonPath("$.terms.monthlyMovementLimit")
        .doesNotExist();
    post(productBody(
            "SME_CHECKING",
            "SME checking account",
            "CHECKING",
            eligibility(List.of("BUSINESS"), "SME", true),
            baseTerms("0.00", "0.00", 50, "0.80", "0.25")))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.terms.monthlyMaintenanceFee")
        .doesNotExist();
    post(fixedTermBody("FIXED_TERM"))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.terms.operationDayOfMonth")
        .isEqualTo(15);

    assertThat(storedCount()).isEqualTo(5);
  }

  @Test
  void nameIsStoredWithoutSurroundingSpaces() {
    Map<String, Object> body = savingsBody("SAVINGS");
    body.put("name", "  Savings account  ");

    post(body)
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Savings account");
  }

  @Test
  void codeAlreadyInTheCatalogReturns409AndKeepsTheFirstProduct() {
    createProduct(savingsBody("SAVINGS"));
    Map<String, Object> other = savingsBody("SAVINGS");
    other.put("name", "Another savings account");

    post(other)
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_CODE_ALREADY_EXISTS")
        .jsonPath("$.path")
        .isEqualTo("/api/v1/products");

    assertThat(storedCount()).isEqualTo(1);
    assertThat(stored("SAVINGS").getString("name")).isEqualTo("Savings account");
  }

  @Test
  void codeOfARetiredProductStaysReserved() {
    Created created = createProduct(savingsBody("SAVINGS"));
    webTestClient
        .delete()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", created.etag())
        .exchange()
        .expectStatus()
        .isNoContent();

    post(savingsBody("SAVINGS"))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_CODE_ALREADY_EXISTS");
  }

  @Test
  void savingsWithoutMovementLimitReturns422() {
    Map<String, Object> body = savingsBody("SAVINGS");
    body.put("terms", baseTerms("0.00", "0.00", 5, "2.50", "0.75"));

    post(body)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_RULE_VIOLATION")
        .jsonPath("$.message")
        .isEqualTo("A SAVINGS product requires a monthly movement limit.");

    assertThat(storedCount()).isZero();
  }

  @Test
  void savingsWithMaintenanceFeeReturns422() {
    Map<String, Object> terms = savingsTerms();
    terms.put("monthlyMaintenanceFee", amounts("12.00", "4.00"));
    Map<String, Object> body = savingsBody("SAVINGS");
    body.put("terms", terms);

    post(body)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_RULE_VIOLATION");
  }

  @Test
  void checkingWithMovementLimitReturns422() {
    Map<String, Object> body = checkingBody("CHECKING");
    body.put("terms", savingsTerms());

    post(body)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_RULE_VIOLATION");
  }

  @Test
  void fixedTermWithoutOperationDayReturns422() {
    Map<String, Object> body = fixedTermBody("FIXED_TERM");
    body.put("terms", baseTerms("1000.00", "300.00", 1, "0.00", "0.00"));

    post(body)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("A FIXED_TERM product requires an operation day of month.");
  }

  @Test
  void requiredProfileTheCustomerTypeCannotHaveReturns422() {
    Map<String, Object> body = checkingBody("CHECKING");
    body.put("eligibility", eligibility(List.of("PERSONAL", "BUSINESS"), "SME", false));

    post(body)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_RULE_VIOLATION")
        .jsonPath("$.message")
        .isEqualTo("Customer profile SME is not available for customer type PERSONAL.");
  }

  @Test
  void amountWithMoreThanTwoDecimalsReturns422() {
    Map<String, Object> terms = savingsTerms();
    terms.put("movementFee", amounts("2.505", "0.75"));
    Map<String, Object> body = savingsBody("SAVINGS");
    body.put("terms", terms);

    post(body)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Movement fee in PEN can have at most two decimals.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"savings", "S", "1SAVINGS", "VIP-SAVINGS", "VIP SAVINGS"})
  void invalidCodeReturns400(String code) {
    post(savingsBody(code))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @ParameterizedTest
  @ValueSource(strings = {"code", "name", "family", "eligibility", "terms"})
  void missingRequiredFieldReturns400(String field) {
    Map<String, Object> body = savingsBody("SAVINGS");
    body.remove(field);

    post(body)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");

    assertThat(storedCount()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // Unknown field, also one the server owns.
        "{\"status\":\"RETIRED\"}",
        "{\"family\":\"CREDIT\"}",
        "{\"name\":\"   \"}",
        "{\"eligibility\":{\"customerTypes\":[],\"requiresCreditCard\":false}}",
        "{\"eligibility\":{\"customerTypes\":[\"PERSONAL\"]}}",
        "{\"eligibility\":{\"customerTypes\":[\"PERSONAL\"],\"requiresCreditCard\":false,\"minimumAge\":18}}",
        "{\"terms\":{\"openingMinimum\":{\"PEN\":0},\"freeMonthlyMovements\":5,"
            + "\"movementFee\":{\"PEN\":2.5,\"USD\":0.75},\"monthlyMovementLimit\":10}}",
        "{\"terms\":{\"openingMinimum\":{\"PEN\":-1,\"USD\":0},\"freeMonthlyMovements\":5,"
            + "\"movementFee\":{\"PEN\":2.5,\"USD\":0.75},\"monthlyMovementLimit\":10}}",
        "{\"terms\":{\"openingMinimum\":{\"PEN\":0,\"USD\":0,\"EUR\":0},\"freeMonthlyMovements\":5,"
            + "\"movementFee\":{\"PEN\":2.5,\"USD\":0.75},\"monthlyMovementLimit\":10}}",
        "{\"terms\":{\"openingMinimum\":{\"PEN\":0,\"USD\":0},\"freeMonthlyMovements\":-1,"
            + "\"movementFee\":{\"PEN\":2.5,\"USD\":0.75},\"monthlyMovementLimit\":10}}",
        "{\"terms\":{\"openingMinimum\":{\"PEN\":0,\"USD\":0},\"freeMonthlyMovements\":5,"
            + "\"movementFee\":{\"PEN\":2.5,\"USD\":0.75},\"monthlyMovementLimit\":0}}"
      })
  void malformedFieldReturns400(String override) {
    // The override replaces top-level fields of a valid savings request.
    String valid =
        """
        {"code":"SAVINGS","name":"Savings account","family":"SAVINGS",
         "eligibility":{"customerTypes":["PERSONAL"],"requiresCreditCard":false},
         "terms":{"openingMinimum":{"PEN":0,"USD":0},"freeMonthlyMovements":5,
                  "movementFee":{"PEN":2.5,"USD":0.75},"monthlyMovementLimit":10}}
        """;
    String body = merge(valid, override);

    webTestClient
        .post()
        .uri("/api/v1/products")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");

    assertThat(storedCount()).isZero();
  }

  @Test
  void malformedJsonReturns400() {
    webTestClient
        .post()
        .uri("/api/v1/products")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"code\":")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void nonJsonContentTypeReturns415() {
    webTestClient
        .post()
        .uri("/api/v1/products")
        .contentType(MediaType.TEXT_PLAIN)
        .bodyValue("code=SAVINGS")
        .exchange()
        .expectStatus()
        .isEqualTo(415)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNSUPPORTED_MEDIA_TYPE");
  }

  private WebTestClient.ResponseSpec post(Map<String, Object> body) {
    return webTestClient
        .post()
        .uri("/api/v1/products")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  /** {@code base} with the top-level fields of {@code override} replaced or added. */
  private static String merge(String base, String override) {
    tools.jackson.databind.json.JsonMapper json =
        tools.jackson.databind.json.JsonMapper.builder().build();
    tools.jackson.databind.node.ObjectNode merged =
        (tools.jackson.databind.node.ObjectNode) json.readTree(base);
    merged.setAll((tools.jackson.databind.node.ObjectNode) json.readTree(override));
    return json.writeValueAsString(merged);
  }
}
