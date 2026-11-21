package com.banking.productservice.support;

import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductResponse;
import com.banking.productservice.infrastructure.adapter.out.mongodb.ProductDocument;
import com.banking.productservice.infrastructure.adapter.out.redis.RedisProductCacheAdapter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole service on a random port against a real MongoDB and a real Redis in Docker.
 *
 * <p>The application keeps its real {@code spring.config.import}. A stub Config Server serves where
 * {@code products_db} and Redis are, and a real Vault holds the MongoDB user and password and the
 * Redis password, which the Config Server never serves. {@code CONFIG_SERVER_URL}, {@code
 * VAULT_URI} and {@code VAULT_APPROLE_FILE} point the client at them. All start once, before any
 * Spring context, and are shared by every subclass.
 *
 * <p>The default products are not added at startup here, so each test starts with an empty catalog
 * and an empty cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(SteppingClockConfiguration.class)
public abstract class ProductIntegrationTest {

  protected static final String DATABASE = "products_db";
  protected static final String USERNAME = "products";
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
  private static final int REDIS_PORT = 6379;
  protected static final MongoDBContainer MONGO =
      new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
  protected static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
          .withExposedPorts(REDIS_PORT)
          // As infra/docker-compose.yml starts it: no access without the password.
          .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);
  protected static final StubConfigServer CONFIG_SERVER;
  protected static final TestVault VAULT;

  static {
    MONGO.start();
    REDIS.start();
    createServiceUser();
    // Read by application.yaml through VAULT_URI and VAULT_APPROLE_FILE.
    VAULT =
        TestVault.start(
            "product-service",
            Map.of(
                "spring.mongodb.username", USERNAME,
                "spring.mongodb.password", PASSWORD,
                "spring.data.redis.password", REDIS_PASSWORD));
    CONFIG_SERVER =
        StubConfigServer.start(
            Map.of(
                "spring.mongodb.host",
                MONGO.getHost(),
                "spring.mongodb.port",
                String.valueOf(MONGO.getMappedPort(27017)),
                "spring.mongodb.database",
                DATABASE,
                "spring.mongodb.authentication-database",
                DATABASE,
                "spring.data.redis.host",
                REDIS.getHost(),
                "spring.data.redis.port",
                String.valueOf(REDIS.getMappedPort(REDIS_PORT)),
                "product.catalog.seed",
                "false",
                // No registry in the tests: the service must not register with a real Eureka.
                "eureka.client.enabled",
                "false"));
    // Read by application.yaml: configserver:${CONFIG_SERVER_URL:http://localhost:8888}
    System.setProperty("CONFIG_SERVER_URL", CONFIG_SERVER.url());
  }

  /**
   * The user the service logs in as, created like infra/mongo/create-product-user.js does. The
   * container does not enforce authorization, but it does check the password.
   */
  private static void createServiceUser() {
    try {
      ExecResult result =
          MONGO.execInContainer(
              "mongosh",
              "--quiet",
              DATABASE,
              "--eval",
              "db.createUser({user: '%s', pwd: '%s', roles: [{role: 'readWrite', db: '%s'}]})"
                  .formatted(USERNAME, PASSWORD, DATABASE));
      if (result.getExitCode() != 0) {
        throw new IllegalStateException("Could not create the MongoDB user: " + result.getStderr());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Autowired protected WebTestClient webTestClient;

  @Autowired protected ReactiveMongoTemplate mongoTemplate;

  @Autowired protected ReactiveStringRedisTemplate redis;

  @BeforeEach
  void emptyCatalogAndCache() {
    mongoTemplate.remove(new Query(), ProductDocument.COLLECTION).block();
    emptyCache();
  }

  /** Overridden by the tests that run without a reachable Redis. */
  protected void emptyCache() {
    redis.keys(RedisProductCacheAdapter.KEY_PREFIX + "*").flatMap(redis::delete).then().block();
  }

  /** Code and ETag of a product created through HTTP. */
  protected record Created(String code, String etag) {}

  protected Created createProduct(Map<String, Object> body) {
    EntityExchangeResult<ProductResponse> result =
        webTestClient
            .post()
            .uri("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(ProductResponse.class)
            .returnResult();

    return new Created(result.getResponseBody().getCode(), result.getResponseHeaders().getETag());
  }

  /** A valid savings product for PERSONAL customers of any profile. */
  protected static Map<String, Object> savingsBody(String code) {
    return productBody(
        code,
        "Savings account",
        "SAVINGS",
        eligibility(List.of("PERSONAL"), null, false),
        savingsTerms());
  }

  /** A valid checking product for PERSONAL and BUSINESS customers, with maintenance fee. */
  protected static Map<String, Object> checkingBody(String code) {
    Map<String, Object> terms = baseTerms("0.00", "0.00", 20, "1.00", "0.30");
    terms.put("monthlyMaintenanceFee", amounts("12.00", "4.00"));
    return productBody(
        code,
        "Checking account",
        "CHECKING",
        eligibility(List.of("PERSONAL", "BUSINESS"), null, false),
        terms);
  }

  /** A valid fixed-term product that operates on day 15. */
  protected static Map<String, Object> fixedTermBody(String code) {
    Map<String, Object> terms = baseTerms("1000.00", "300.00", 1, "0.00", "0.00");
    terms.put("operationDayOfMonth", 15);
    return productBody(
        code,
        "Fixed-term deposit",
        "FIXED_TERM",
        eligibility(List.of("PERSONAL"), null, false),
        terms);
  }

  protected static Map<String, Object> productBody(
      String code,
      String name,
      String family,
      Map<String, Object> eligibility,
      Map<String, Object> terms) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("name", name);
    body.put("family", family);
    body.put("eligibility", eligibility);
    body.put("terms", terms);
    return body;
  }

  /** Body of PUT /api/v1/products/{code}: the editable fields of {@code product}. */
  protected static Map<String, Object> updateBody(Map<String, Object> product) {
    Map<String, Object> body = new LinkedHashMap<>(product);
    body.remove("code");
    body.remove("family");
    return body;
  }

  protected static Map<String, Object> eligibility(
      List<String> customerTypes, String requiredProfile, boolean requiresCreditCard) {
    Map<String, Object> eligibility = new LinkedHashMap<>();
    eligibility.put("customerTypes", customerTypes);
    if (requiredProfile != null) {
      eligibility.put("requiredProfile", requiredProfile);
    }
    eligibility.put("requiresCreditCard", requiresCreditCard);
    return eligibility;
  }

  /** Savings terms: no opening minimum, 5 free movements, fee 2.50 / 0.75, limit 10. */
  protected static Map<String, Object> savingsTerms() {
    Map<String, Object> terms = baseTerms("0.00", "0.00", 5, "2.50", "0.75");
    terms.put("monthlyMovementLimit", 10);
    return terms;
  }

  protected static Map<String, Object> baseTerms(
      String openingPen,
      String openingUsd,
      int freeMonthlyMovements,
      String feePen,
      String feeUsd) {
    Map<String, Object> terms = new LinkedHashMap<>();
    terms.put("openingMinimum", amounts(openingPen, openingUsd));
    terms.put("freeMonthlyMovements", freeMonthlyMovements);
    terms.put("movementFee", amounts(feePen, feeUsd));
    return terms;
  }

  /** One amount per currency, sent as JSON numbers. */
  protected static Map<String, Object> amounts(String pen, String usd) {
    Map<String, Object> amounts = new LinkedHashMap<>();
    amounts.put("PEN", new java.math.BigDecimal(pen));
    amounts.put("USD", new java.math.BigDecimal(usd));
    return amounts;
  }

  /** Raw stored document, read straight from MongoDB. */
  protected Document stored(String code) {
    return mongoTemplate.findById(code, Document.class, ProductDocument.COLLECTION).block();
  }

  protected long storedCount() {
    return mongoTemplate.count(new Query(), ProductDocument.COLLECTION).block();
  }

  /** Raw cached value, read straight from Redis; {@code null} when the product is not cached. */
  protected String cached(String code) {
    return redis.opsForValue().get(RedisProductCacheAdapter.KEY_PREFIX + code).block();
  }
}
