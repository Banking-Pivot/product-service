package com.banking.productservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.productservice.support.ProductIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * The client imports where its MongoDB and its Redis are from Config Server and their credentials
 * from Vault, both through spring.config.import, and logs in to both with them.
 */
class ConfigServerClientIntegrationTest extends ProductIntegrationTest {

  @Autowired private Environment environment;

  @Test
  void importsMongoAndRedisLocationsFromConfigServer() {
    assertThat(environment.getProperty("spring.application.name")).isEqualTo("product-service");
    assertThat(CONFIG_SERVER.requestedPaths()).contains("/product-service/default");
    assertThat(environment.getProperty("spring.mongodb.host")).isEqualTo(MONGO.getHost());
    assertThat(environment.getProperty("spring.mongodb.port", Integer.class))
        .isEqualTo(MONGO.getMappedPort(27017));
    assertThat(sourceOf("spring.mongodb.database")).contains("configserver");
    assertThat(environment.getProperty("spring.data.redis.port", Integer.class))
        .isEqualTo(REDIS.getMappedPort(6379));
    assertThat(sourceOf("spring.data.redis.host")).contains("configserver");
  }

  /** The user and password come from Vault; the Config Server never serves them. */
  @Test
  void logsInToMongoWithTheCredentialsFromVault() {
    assertThat(sourceOf("spring.mongodb.username")).contains("secret/product-service");
    assertThat(sourceOf("spring.mongodb.password")).contains("secret/product-service");
    Document status = mongoTemplate.executeCommand("{connectionStatus: 1}").block();
    assertThat(status.get("authInfo", Document.class).getList("authenticatedUsers", Document.class))
        .extracting(user -> user.getString("user"))
        .containsExactly(USERNAME);
  }

  /** Redis rejects every command without its password, so answering PING proves the login. */
  @Test
  void logsInToRedisWithThePasswordFromVault() {
    assertThat(sourceOf("spring.data.redis.password")).contains("secret/product-service");
    assertThat(redis.getConnectionFactory().getReactiveConnection().ping().block())
        .isEqualTo("PONG");
  }

  @Test
  void connectsToProductsDatabase() {
    assertThat(mongoTemplate.getMongoDatabase().block().getName()).isEqualTo(DATABASE);
  }

  @Test
  void cacheSettingsHaveDefaultsWhenTheConfigServerDoesNotServeThem() {
    assertThat(environment.getProperty("product.cache.ttl")).isNull();
    assertThat(cacheProperties.ttl()).hasMinutes(10);
    assertThat(cacheProperties.timeout()).hasMillis(500);
  }

  @Autowired private ProductCacheProperties cacheProperties;

  /** Name of the first (winning) property source that defines {@code key}. */
  private String sourceOf(String key) {
    return ((ConfigurableEnvironment) environment)
        .getPropertySources().stream()
            // Boot's view over all the other sources, not a source of its own.
            .filter(source -> !"configurationProperties".equals(source.getName()))
            .filter(source -> source.containsProperty(key))
            .map(PropertySource::getName)
            .findFirst()
            .orElse("none");
  }
}
