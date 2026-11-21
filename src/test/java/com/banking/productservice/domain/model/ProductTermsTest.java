package com.banking.productservice.domain.model;

import static com.banking.productservice.support.Products.amounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.productservice.domain.exception.InvalidProductException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTermsTest {

  @Test
  void amountsAreKeptWithTwoDecimals() {
    ProductTerms terms =
        new ProductTerms(
            amounts("1000", "300.5"),
            5,
            amounts("2.50", "0.750"),
            10,
            null,
            amounts("1000.0", "300"),
            null);

    assertThat(terms.openingMinimum())
        .containsEntry(CurrencyCode.PEN, new BigDecimal("1000.00"))
        .containsEntry(CurrencyCode.USD, new BigDecimal("300.50"));
    assertThat(terms.movementFee()).containsEntry(CurrencyCode.USD, new BigDecimal("0.75"));
    assertThat(terms.minimumDailyAverageBalance())
        .containsEntry(CurrencyCode.PEN, new BigDecimal("1000.00"));
    assertThat(terms.monthlyMaintenanceFee()).isNull();
  }

  @Test
  void termsWithTheSameAmountsAreEqualWhateverTheScaleTheyWereGivenWith() {
    assertThat(new ProductTerms(amounts("0", "0"), 5, amounts("2.5", "0.75"), 10, null, null, null))
        .isEqualTo(
            new ProductTerms(
                amounts("0.00", "0.00"), 5, amounts("2.50", "0.750"), 10, null, null, null));
  }

  @Test
  void everyCurrencyNeedsAnAmount() {
    assertThatThrownBy(
            () ->
                new ProductTerms(
                    Map.of(CurrencyCode.PEN, BigDecimal.ZERO),
                    5,
                    amounts("2.50", "0.75"),
                    10,
                    null,
                    null,
                    null))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("Opening minimum in USD is required.");
    assertThatThrownBy(
            () -> new ProductTerms(null, 5, amounts("2.50", "0.75"), 10, null, null, null))
        .isInstanceOf(InvalidProductException.class);
    assertThatThrownBy(() -> new ProductTerms(amounts("0", "0"), 5, null, 10, null, null, null))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void amountsCannotBeNegativeNorHaveMoreThanTwoDecimalsNorExceedTheMaximum() {
    assertThatThrownBy(
            () ->
                new ProductTerms(
                    amounts("-0.01", "0"), 5, amounts("2.50", "0.75"), 10, null, null, null))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("Opening minimum in PEN cannot be negative.");
    assertThatThrownBy(
            () ->
                new ProductTerms(
                    amounts("0", "0"), 5, amounts("2.505", "0.75"), 10, null, null, null))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("Movement fee in PEN can have at most two decimals.");
    assertThatThrownBy(
            () ->
                new ProductTerms(
                    amounts("0", "1000000000000.00"),
                    5,
                    amounts("2.50", "0.75"),
                    10,
                    null,
                    null,
                    null))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("Opening minimum in USD exceeds the maximum amount.");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 100_001})
  void freeMonthlyMovementsOutOfRangeAreRejected(int movements) {
    assertThatThrownBy(
            () ->
                new ProductTerms(
                    amounts("0", "0"), movements, amounts("2.50", "0.75"), 10, null, null, null))
        .isInstanceOf(InvalidProductException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -5, 100_001})
  void monthlyMovementLimitOutOfRangeIsRejected(int limit) {
    assertThatThrownBy(
            () ->
                new ProductTerms(
                    amounts("0", "0"), 5, amounts("2.50", "0.75"), limit, null, null, null))
        .isInstanceOf(InvalidProductException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 32})
  void operationDayOutOfTheMonthIsRejected(int day) {
    assertThatThrownBy(
            () -> new ProductTerms(amounts("0", "0"), 1, amounts("0", "0"), null, null, null, day))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void amountsCannotBeChangedFromOutside() {
    ProductTerms terms =
        new ProductTerms(amounts("0", "0"), 5, amounts("2.50", "0.75"), 10, null, null, null);

    assertThatThrownBy(() -> terms.movementFee().put(CurrencyCode.PEN, BigDecimal.TEN))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
