package com.banking.productservice.domain.model;

import com.banking.productservice.domain.exception.InvalidProductException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Money rules of the catalog: two decimals, never rounded silently, one amount per currency. */
public final class Amounts {

  /** Decimal places of every amount. */
  public static final int SCALE = 2;

  /** Largest amount a product term can quote. */
  public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");

  private Amounts() {}

  /**
   * Validates one amount per currency and returns them with scale 2, in currency order.
   *
   * @param name what the amounts are, used in the error message
   * @throws InvalidProductException when a currency is missing or its amount is negative, has more
   *     than two decimals or exceeds the maximum
   */
  public static Map<CurrencyCode, BigDecimal> perCurrency(
      Map<CurrencyCode, BigDecimal> amounts, String name) {
    if (amounts == null) {
      throw new InvalidProductException(name + " is required.");
    }
    Map<CurrencyCode, BigDecimal> normalized = new EnumMap<>(CurrencyCode.class);
    for (CurrencyCode currency : CurrencyCode.values()) {
      normalized.put(currency, amount(amounts.get(currency), name + " in " + currency));
    }
    return Collections.unmodifiableMap(normalized);
  }

  private static BigDecimal amount(BigDecimal amount, String name) {
    if (amount == null) {
      throw new InvalidProductException(name + " is required.");
    }
    if (amount.signum() < 0) {
      throw new InvalidProductException(name + " cannot be negative.");
    }
    if (amount.stripTrailingZeros().scale() > SCALE) {
      throw new InvalidProductException(name + " can have at most two decimals.");
    }
    if (amount.compareTo(MAX_AMOUNT) > 0) {
      throw new InvalidProductException(name + " exceeds the maximum amount.");
    }
    return amount.setScale(SCALE);
  }
}
