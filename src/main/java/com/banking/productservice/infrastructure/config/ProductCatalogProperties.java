package com.banking.productservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code product.catalog.*} settings, served by the Config Server ({@code product-service.yml}).
 *
 * @param seed whether the service adds the default products that are missing when it starts
 */
@ConfigurationProperties("product.catalog")
public record ProductCatalogProperties(@DefaultValue("true") boolean seed) {}
