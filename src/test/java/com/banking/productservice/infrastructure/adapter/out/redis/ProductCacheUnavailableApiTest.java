package com.banking.productservice.infrastructure.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.support.ProductIntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole service with Redis unreachable: the cache only makes reads faster, so every operation
 * still answers from MongoDB.
 */
class ProductCacheUnavailableApiTest extends ProductIntegrationTest {

  /** Nothing listens on this port. */
  private static final int CLOSED_PORT = closedPort();

  @DynamicPropertySource
  static void pointRedisAtAClosedPort(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.port", () -> CLOSED_PORT);
    registry.add("product.cache.timeout", () -> "300ms");
  }

  @Override
  protected void emptyCache() {
    // No Redis to clean.
  }

  @Test
  void readsAnswerFromMongoWhenTheCacheIsDown() {
    createProduct(savingsBody("SAVINGS"));

    for (int read = 0; read < 2; read++) {
      webTestClient
          .get()
          .uri("/api/v1/products/SAVINGS")
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .valueEquals("ETag", "\"0\"")
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("SAVINGS")
          .jsonPath("$.terms.monthlyMovementLimit")
          .isEqualTo(10);
    }
  }

  @Test
  void unknownProductIsStill404WhenTheCacheIsDown() {
    webTestClient
        .get()
        .uri("/api/v1/products/UNKNOWN")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("PRODUCT_NOT_FOUND");
  }

  @Test
  void updateAndRetirementAreStoredWhenTheCacheIsDown() {
    Created created = createProduct(savingsBody("SAVINGS"));
    Map<String, Object> body = updateBody(savingsBody("SAVINGS"));
    body.put("name", "Everyday savings");

    webTestClient
        .put()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", created.etag())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"1\"");

    webTestClient
        .delete()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", "\"1\"")
        .exchange()
        .expectStatus()
        .isNoContent();

    assertThat(stored("SAVINGS").getString("name")).isEqualTo("Everyday savings");
    assertThat(stored("SAVINGS").getString("status")).isEqualTo("RETIRED");
    assertThat(stored("SAVINGS").get("version", Number.class).longValue()).isEqualTo(2);
  }

  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
