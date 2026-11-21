package com.banking.productservice.infrastructure.adapter.out.redis;

import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.port.out.ProductCachePort;
import com.banking.productservice.infrastructure.config.ProductCacheProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Product cache in Redis: one JSON value per product under {@code product:<code>}, with the
 * lifetime of {@code product.cache.ttl}.
 *
 * <p>Redis only accelerates reads, so every call is bounded by {@code product.cache.timeout} and a
 * failure (Redis down, slow, or holding a value this version cannot read) is logged and treated as
 * "not cached": the caller answers from MongoDB.
 */
@Slf4j
@Component
public class RedisProductCacheAdapter implements ProductCachePort {

  public static final String KEY_PREFIX = "product:";

  private final ReactiveStringRedisTemplate redis;
  private final Duration ttl;
  private final Duration timeout;
  // Own mapper: the stored format must not change with the settings of the HTTP JSON.
  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * Takes from the cache settings how long a product stays cached and the time limit of each Redis
   * call.
   */
  public RedisProductCacheAdapter(
      ReactiveStringRedisTemplate redis, ProductCacheProperties properties) {
    this.redis = redis;
    this.ttl = properties.ttl();
    this.timeout = properties.timeout();
  }

  @Override
  public Mono<ProductView> find(String code) {
    return Mono.defer(() -> redis.opsForValue().get(key(code)))
        .map(value -> json.readValue(value, ProductView.class))
        .timeout(timeout)
        .onErrorResume(failure -> unavailable("read", code, failure));
  }

  @Override
  public Mono<Void> store(ProductView view) {
    return Mono.defer(
            () -> redis.opsForValue().set(key(view.code()), json.writeValueAsString(view), ttl))
        .timeout(timeout)
        .then()
        .onErrorResume(failure -> unavailable("store", view.code(), failure));
  }

  @Override
  public Mono<Void> evict(String code) {
    return Mono.defer(() -> redis.delete(key(code)))
        .timeout(timeout)
        .then()
        .onErrorResume(failure -> unavailable("evict", code, failure));
  }

  static String key(String code) {
    return KEY_PREFIX + code;
  }

  private static <T> Mono<T> unavailable(String operation, String code, Throwable failure) {
    log.warn(
        "Product cache {} failed for {}; answering without it. cause={}",
        operation,
        code,
        failure.toString());
    return Mono.empty();
  }
}
