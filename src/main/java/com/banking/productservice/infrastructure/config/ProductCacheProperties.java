package com.banking.productservice.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code product.cache.*} settings, served by the Config Server ({@code product-service.yml}).
 *
 * @param ttl how long a product stays in the cache; also the longest a stale product can be served
 *     if it could not be removed after a change
 * @param timeout limit per cache call before the service answers from the database instead
 */
@Validated
@ConfigurationProperties("product.cache")
public record ProductCacheProperties(
    @NotNull @DurationMin(seconds = 1) @DefaultValue("10m") Duration ttl,
    @NotNull @DurationMin(millis = 1) @DefaultValue("500ms") Duration timeout) {}
