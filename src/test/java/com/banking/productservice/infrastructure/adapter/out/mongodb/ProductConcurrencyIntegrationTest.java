package com.banking.productservice.infrastructure.adapter.out.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.application.port.out.ProductWritePort;
import com.banking.productservice.domain.model.Product;
import com.banking.productservice.support.ProductIntegrationTest;
import com.banking.productservice.support.SteppingClockConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Race conditions resolved by MongoDB itself: the document ID for concurrent creations of one code
 * and the version-conditioned findAndModify for concurrent updates. Requests are fired concurrently
 * through a non-blocking client against the running server.
 */
class ProductConcurrencyIntegrationTest extends ProductIntegrationTest {

  private static final Instant LATER = SteppingClockConfiguration.START.plus(Duration.ofDays(1));

  @LocalServerPort private int port;

  @Autowired private ProductWritePort writePort;

  private WebClient client;

  @BeforeEach
  void createClient() {
    client = WebClient.create("http://localhost:" + port);
  }

  @RepeatedTest(10)
  void simultaneousCreationsOfTheSameCodeYieldOne201AndOne409(RepetitionInfo repetition) {
    String code = "SAVINGS_" + repetition.getCurrentRepetition();
    Map<String, Object> first = savingsBody(code);
    first.put("name", "Writer A");
    Map<String, Object> second = savingsBody(code);
    second.put("name", "Writer B");

    List<Integer> statuses =
        Mono.zip(post(first), post(second))
            .map(pair -> List.of(pair.getT1(), pair.getT2()))
            .block(Duration.ofSeconds(10));

    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(storedCount()).isEqualTo(1);
  }

  @RepeatedTest(10)
  void simultaneousUpdatesWithTheSameETagYieldOne200AndOne412WithoutLostUpdate() {
    Created created = createProduct(savingsBody("SAVINGS"));

    List<Outcome> outcomes =
        Mono.zip(put(created.etag(), "Writer A"), put(created.etag(), "Writer B"))
            .map(pair -> List.of(pair.getT1(), pair.getT2()))
            .block(Duration.ofSeconds(10));

    assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(200, 412);
    String winner =
        outcomes.stream()
            .filter(outcome -> outcome.status() == 200)
            .findFirst()
            .orElseThrow()
            .name();

    Document stored = stored("SAVINGS");
    assertThat(stored.getString("name")).isEqualTo(winner);
    assertThat(stored.get("version", Number.class).longValue()).isEqualTo(1);
  }

  @Test
  void writeIsConditionedOnTheClientRevisionEvenAfterReReadingTheDocument() {
    createProduct(savingsBody("SAVINGS"));
    long clientRevision = 0;

    // Another writer moves the product to revision 1.
    Product competitor = writePort.findByCode("SAVINGS").block();
    competitor.update("Competitor", competitor.getEligibility(), competitor.getTerms(), LATER);
    assertThat(writePort.update(competitor, clientRevision).block().getVersion()).isEqualTo(1);

    // The handler re-reads (revision 1) but keeps the client's revision as condition.
    Product reRead = writePort.findByCode("SAVINGS").block();
    reRead.update("Late writer", reRead.getEligibility(), reRead.getTerms(), LATER);

    StepVerifier.create(writePort.update(reRead, clientRevision)).verifyComplete();

    Document stored = stored("SAVINGS");
    assertThat(stored.getString("name")).isEqualTo("Competitor");
    assertThat(stored.get("version", Number.class).longValue()).isEqualTo(1);
  }

  @Test
  void storedProductIsRebuiltWithTheSameEligibilityAndTerms() {
    createProduct(checkingBody("CHECKING"));
    createProduct(fixedTermBody("FIXED_TERM"));

    Product checking = writePort.findByCode("CHECKING").block();
    Product fixedTerm = writePort.findByCode("FIXED_TERM").block();

    assertThat(checking.getEligibility().customerTypes()).hasSize(2);
    assertThat(checking.getTerms().monthlyMaintenanceFee().values())
        .extracting(Object::toString)
        .containsExactly("12.00", "4.00");
    assertThat(checking.getTerms().monthlyMovementLimit()).isNull();
    assertThat(fixedTerm.getTerms().operationDayOfMonth()).isEqualTo(15);
    assertThat(fixedTerm.getVersion()).isZero();
  }

  private Mono<Integer> post(Map<String, Object> body) {
    return client
        .post()
        .uri("/api/v1/products")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchangeToMono(
            response -> response.releaseBody().thenReturn(response.statusCode().value()));
  }

  private Mono<Outcome> put(String etag, String name) {
    Map<String, Object> body = updateBody(savingsBody("SAVINGS"));
    body.put("name", name);
    return client
        .put()
        .uri("/api/v1/products/SAVINGS")
        .header("If-Match", etag)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchangeToMono(
            response ->
                response
                    .releaseBody()
                    .thenReturn(new Outcome(response.statusCode().value(), name)));
  }

  private record Outcome(int status, String name) {}
}
