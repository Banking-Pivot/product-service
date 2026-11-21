package com.banking.productservice.infrastructure.adapter.in.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.application.port.in.CreateProductUseCase;
import com.banking.productservice.infrastructure.adapter.in.rest.ProductRestMapper;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductPageResponse;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductResponse;
import com.banking.productservice.infrastructure.config.ProductCatalogProperties;
import com.banking.productservice.support.ProductIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** The default products the service adds to a catalog that lacks them. */
class ProductCatalogSeederTest extends ProductIntegrationTest {

  @Autowired private ProductCatalogSeeder seeder;

  @Autowired private CreateProductUseCase createProductUseCase;

  @Autowired private ProductRestMapper mapper;

  @Autowired private JsonMapper json;

  @Test
  void addsTheFiveProductsOfTheBankToAnEmptyCatalog() {
    assertThat(seeder.seed().block()).isEqualTo(5);

    ProductPageResponse page =
        webTestClient
            .get()
            .uri("/api/v1/products")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ProductPageResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(page.getItems())
        .extracting(ProductResponse::getCode)
        .containsExactly("CHECKING", "FIXED_TERM", "SAVINGS", "SME_CHECKING", "VIP_SAVINGS");
    assertThat(page.getItems())
        .allSatisfy(product -> assertThat(product.getStatus().name()).isEqualTo("ACTIVE"));
  }

  @Test
  void defaultProductsCarryTheTermsOfTheBank() {
    seeder.seed().block();

    webTestClient
        .get()
        .uri("/api/v1/products/SAVINGS")
        .exchange()
        .expectBody()
        .jsonPath("$.family")
        .isEqualTo("SAVINGS")
        .jsonPath("$.eligibility.customerTypes[0]")
        .isEqualTo("PERSONAL")
        .jsonPath("$.eligibility.requiredProfile")
        .doesNotExist()
        .jsonPath("$.eligibility.requiresCreditCard")
        .isEqualTo(false)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(5)
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(2.5)
        .jsonPath("$.terms.movementFee.USD")
        .isEqualTo(0.75)
        .jsonPath("$.terms.monthlyMovementLimit")
        .isEqualTo(10);
    webTestClient
        .get()
        .uri("/api/v1/products/VIP_SAVINGS")
        .exchange()
        .expectBody()
        .jsonPath("$.family")
        .isEqualTo("SAVINGS")
        .jsonPath("$.eligibility.requiredProfile")
        .isEqualTo("VIP")
        .jsonPath("$.eligibility.requiresCreditCard")
        .isEqualTo(true)
        .jsonPath("$.terms.openingMinimum.PEN")
        .isEqualTo(1000.0)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(10)
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(1.5)
        .jsonPath("$.terms.monthlyMovementLimit")
        .isEqualTo(20)
        .jsonPath("$.terms.minimumDailyAverageBalance.PEN")
        .isEqualTo(1000.0)
        .jsonPath("$.terms.minimumDailyAverageBalance.USD")
        .isEqualTo(300.0);
    webTestClient
        .get()
        .uri("/api/v1/products/CHECKING")
        .exchange()
        .expectBody()
        .jsonPath("$.family")
        .isEqualTo("CHECKING")
        .jsonPath("$.eligibility.customerTypes.length()")
        .isEqualTo(2)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(20)
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(1.0)
        .jsonPath("$.terms.monthlyMaintenanceFee.PEN")
        .isEqualTo(12.0)
        .jsonPath("$.terms.monthlyMaintenanceFee.USD")
        .isEqualTo(4.0);
    webTestClient
        .get()
        .uri("/api/v1/products/SME_CHECKING")
        .exchange()
        .expectBody()
        .jsonPath("$.family")
        .isEqualTo("CHECKING")
        .jsonPath("$.eligibility.customerTypes[0]")
        .isEqualTo("BUSINESS")
        .jsonPath("$.eligibility.requiredProfile")
        .isEqualTo("SME")
        .jsonPath("$.eligibility.requiresCreditCard")
        .isEqualTo(true)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(50)
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(0.8)
        .jsonPath("$.terms.monthlyMaintenanceFee")
        .doesNotExist();
    webTestClient
        .get()
        .uri("/api/v1/products/FIXED_TERM")
        .exchange()
        .expectBody()
        .jsonPath("$.family")
        .isEqualTo("FIXED_TERM")
        .jsonPath("$.terms.openingMinimum.USD")
        .isEqualTo(300.0)
        .jsonPath("$.terms.freeMonthlyMovements")
        .isEqualTo(1)
        .jsonPath("$.terms.movementFee.PEN")
        .isEqualTo(0.0)
        .jsonPath("$.terms.operationDayOfMonth")
        .isEqualTo(15);
  }

  @Test
  void runningAgainAddsNothing() {
    seeder.seed().block();

    assertThat(seeder.seed().block()).isZero();
    assertThat(storedCount()).isEqualTo(5);
  }

  @Test
  void addsOnlyTheMissingProductsAndLeavesEditedAndRetiredOnesAsTheyAre() {
    Map<String, Object> edited = savingsBody("SAVINGS");
    edited.put("name", "Edited by the bank");
    createProduct(edited);
    Created retired = createProduct(fixedTermBody("FIXED_TERM"));
    webTestClient
        .delete()
        .uri("/api/v1/products/FIXED_TERM")
        .header("If-Match", retired.etag())
        .exchange()
        .expectStatus()
        .isNoContent();

    assertThat(seeder.seed().block()).isEqualTo(3);

    assertThat(storedCount()).isEqualTo(5);
    assertThat(stored("SAVINGS").getString("name")).isEqualTo("Edited by the bank");
    assertThat(stored("SAVINGS").get("version", Number.class).longValue()).isZero();
    assertThat(stored("FIXED_TERM").getString("status")).isEqualTo("RETIRED");
  }

  @Test
  void doesNothingAtStartupWhenSeedingIsTurnedOff() {
    // This context runs with product.catalog.seed=false, as every integration test does.
    seeder.run(null);

    assertThat(storedCount()).isZero();
  }

  @Test
  void addsTheDefaultsAtStartupWhenSeedingIsOn() {
    new ProductCatalogSeeder(createProductUseCase, mapper, json, new ProductCatalogProperties(true))
        .run(null);

    assertThat(storedCount()).isEqualTo(5);
  }

  @Test
  void defaultProductsAreAcceptedByTheApiAsTheyAreWritten() throws Exception {
    byte[] catalog =
        new org.springframework.core.io.ClassPathResource(ProductCatalogSeeder.DEFAULT_CATALOG)
            .getContentAsByteArray();

    for (var product : json.readTree(catalog)) {
      webTestClient
          .post()
          .uri("/api/v1/products")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(json.writeValueAsString(product))
          .exchange()
          .expectStatus()
          .isCreated();
    }

    assertThat(storedCount()).isEqualTo(5);
  }
}
