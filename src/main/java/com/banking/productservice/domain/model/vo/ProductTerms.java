package com.banking.productservice.domain.model.vo;

import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.Amounts;
import com.banking.productservice.domain.model.FamilyRule;
import com.banking.productservice.domain.model.enums.CurrencyCode;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Terms of a product. Amounts are quoted for every currency, with scale 2.
 *
 * <p>This record checks each value on its own. Which optional terms a product must or must not
 * define depends on its family: see {@link FamilyRule}.
 *
 * @param openingMinimum minimum initial deposit, can be zero
 * @param freeMonthlyMovements movements per month without fee (quota)
 * @param movementFee fee for each movement beyond the quota
 * @param monthlyMovementLimit hard monthly limit, {@code null} when there is none
 * @param monthlyMaintenanceFee monthly maintenance fee, {@code null} when exempt
 * @param minimumDailyAverageBalance average daily balance required each month, {@code null} when
 *     there is none
 * @param operationDayOfMonth day that accepts the monthly movement, {@code null} when movements are
 *     accepted any day
 */
public record ProductTerms(
    Map<CurrencyCode, BigDecimal> openingMinimum,
    int freeMonthlyMovements,
    Map<CurrencyCode, BigDecimal> movementFee,
    Integer monthlyMovementLimit,
    Map<CurrencyCode, BigDecimal> monthlyMaintenanceFee,
    Map<CurrencyCode, BigDecimal> minimumDailyAverageBalance,
    Integer operationDayOfMonth) {

  public static final int MAX_MONTHLY_MOVEMENTS = 100_000;

  /**
   * Validates the amounts and keeps them with scale 2 (see {@link Amounts#perCurrency}), and
   * rejects free movements outside 0..{@link #MAX_MONTHLY_MOVEMENTS}, a movement limit outside
   * 1..{@link #MAX_MONTHLY_MOVEMENTS} and an operation day outside 1..31. Optional terms left
   * {@code null} are not checked.
   */
  public ProductTerms {
    openingMinimum = Amounts.perCurrency(openingMinimum, "Opening minimum");
    movementFee = Amounts.perCurrency(movementFee, "Movement fee");
    if (monthlyMaintenanceFee != null) {
      monthlyMaintenanceFee = Amounts.perCurrency(monthlyMaintenanceFee, "Monthly maintenance fee");
    }
    if (minimumDailyAverageBalance != null) {
      minimumDailyAverageBalance =
          Amounts.perCurrency(minimumDailyAverageBalance, "Minimum daily average balance");
    }
    if (freeMonthlyMovements < 0 || freeMonthlyMovements > MAX_MONTHLY_MOVEMENTS) {
      throw new InvalidProductException(
          "Free monthly movements must be between 0 and " + MAX_MONTHLY_MOVEMENTS + ".");
    }
    if (monthlyMovementLimit != null
        && (monthlyMovementLimit < 1 || monthlyMovementLimit > MAX_MONTHLY_MOVEMENTS)) {
      throw new InvalidProductException(
          "Monthly movement limit must be between 1 and " + MAX_MONTHLY_MOVEMENTS + ".");
    }
    if (operationDayOfMonth != null && (operationDayOfMonth < 1 || operationDayOfMonth > 31)) {
      throw new InvalidProductException("Operation day of month must be between 1 and 31.");
    }
  }
}
