package com.banking.productservice.support;

import com.banking.productservice.domain.model.CurrencyCode;
import com.banking.productservice.domain.model.CustomerType;
import com.banking.productservice.domain.model.Eligibility;
import com.banking.productservice.domain.model.Product;
import com.banking.productservice.domain.model.ProductFamily;
import com.banking.productservice.domain.model.ProductTerms;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Valid products, eligibilities and terms for unit tests. */
public final class Products {

  public static final Instant T0 = Instant.parse("2026-11-05T15:00:00Z");

  private Products() {}

  public static Map<CurrencyCode, BigDecimal> amounts(String pen, String usd) {
    Map<CurrencyCode, BigDecimal> amounts = new EnumMap<>(CurrencyCode.class);
    amounts.put(CurrencyCode.PEN, new BigDecimal(pen));
    amounts.put(CurrencyCode.USD, new BigDecimal(usd));
    return amounts;
  }

  /** PERSONAL customers of any profile, no credit card required. */
  public static Eligibility personal() {
    return new Eligibility(Set.of(CustomerType.PERSONAL), null, false);
  }

  /** No opening minimum, 5 free movements, fee 2.50 / 0.75, limit 10. */
  public static ProductTerms savingsTerms() {
    return new ProductTerms(
        amounts("0.00", "0.00"), 5, amounts("2.50", "0.75"), 10, null, null, null);
  }

  /** No opening minimum, 20 free movements, fee 1.00 / 0.30, maintenance 12.00 / 4.00. */
  public static ProductTerms checkingTerms() {
    return new ProductTerms(
        amounts("0.00", "0.00"),
        20,
        amounts("1.00", "0.30"),
        null,
        amounts("12.00", "4.00"),
        null,
        null);
  }

  /** Opening minimum 1000.00 / 300.00, one free movement, operation day 15. */
  public static ProductTerms fixedTermTerms() {
    return new ProductTerms(
        amounts("1000.00", "300.00"), 1, amounts("0.00", "0.00"), null, null, null, 15);
  }

  /** An active savings product created at {@link #T0}, revision 0. */
  public static Product savings(String code) {
    return Product.create(
        code, "Savings account", ProductFamily.SAVINGS, personal(), savingsTerms(), T0);
  }

  /** The same product as the store returns it at {@code version}. */
  public static Product atRevision(Product product, long version) {
    return Product.restore(
        product.getCode(),
        product.getName(),
        product.getFamily(),
        product.getEligibility(),
        product.getTerms(),
        product.getStatus(),
        product.getCreatedAt(),
        product.getUpdatedAt(),
        version);
  }
}
