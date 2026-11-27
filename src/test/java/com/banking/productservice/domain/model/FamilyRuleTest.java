package com.banking.productservice.domain.model;

import static com.banking.productservice.support.Products.amounts;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.enums.CurrencyCode;
import com.banking.productservice.domain.model.enums.ProductFamily;
import com.banking.productservice.domain.model.vo.ProductTerms;
import com.banking.productservice.support.Products;
import org.junit.jupiter.api.Test;

/** The rule each family applies to the optional terms of its products. */
class FamilyRuleTest {

  @Test
  void savingsRequiresALimitAndMayRequireAnAverageBalance() {
    FamilyRule rule = ProductFamily.SAVINGS.rule();

    assertThatCode(() -> rule.check(Products.savingsTerms())).doesNotThrowAnyException();
    assertThatCode(() -> rule.check(terms(10, null, amounts("1000.00", "300.00"), null)))
        .doesNotThrowAnyException();

    assertRejected(
        rule,
        terms(null, null, null, null),
        "A SAVINGS product requires a monthly movement limit.");
    assertRejected(
        rule,
        terms(10, amounts("12.00", "4.00"), null, null),
        "A SAVINGS product is exempt from the maintenance fee.");
    assertRejected(
        rule,
        terms(10, null, null, 15),
        "A SAVINGS product cannot have an operation day of month.");
  }

  @Test
  void checkingHasNoLimitAndMayChargeMaintenance() {
    FamilyRule rule = ProductFamily.CHECKING.rule();

    assertThatCode(() -> rule.check(Products.checkingTerms())).doesNotThrowAnyException();
    // SME checking: exempt from the maintenance fee.
    assertThatCode(() -> rule.check(terms(null, null, null, null))).doesNotThrowAnyException();

    assertRejected(
        rule,
        terms(10, null, null, null),
        "A CHECKING product cannot have a monthly movement limit.");
    assertRejected(
        rule,
        terms(null, null, amounts("1000.00", "300.00"), null),
        "A CHECKING product cannot have a minimum daily average balance.");
    assertRejected(
        rule,
        terms(null, null, null, 15),
        "A CHECKING product cannot have an operation day of month.");
  }

  @Test
  void fixedTermRequiresAnOperationDayAndNothingElse() {
    FamilyRule rule = ProductFamily.FIXED_TERM.rule();

    assertThatCode(() -> rule.check(Products.fixedTermTerms())).doesNotThrowAnyException();

    assertRejected(
        rule,
        terms(null, null, null, null),
        "A FIXED_TERM product requires an operation day of month.");
    assertRejected(
        rule,
        terms(1, null, null, 15),
        "A FIXED_TERM product cannot have a monthly movement limit: it allows one movement per"
            + " month.");
    assertRejected(
        rule,
        terms(null, amounts("12.00", "4.00"), null, 15),
        "A FIXED_TERM product is exempt from the maintenance fee.");
    assertRejected(
        rule,
        terms(null, null, amounts("1000.00", "300.00"), 15),
        "A FIXED_TERM product cannot have a minimum daily average balance.");
  }

  private static ProductTerms terms(
      Integer limit,
      java.util.Map<CurrencyCode, java.math.BigDecimal> maintenanceFee,
      java.util.Map<CurrencyCode, java.math.BigDecimal> minimumAverage,
      Integer operationDay) {
    return new ProductTerms(
        amounts("0.00", "0.00"),
        5,
        amounts("1.00", "0.30"),
        limit,
        maintenanceFee,
        minimumAverage,
        operationDay);
  }

  private static void assertRejected(FamilyRule rule, ProductTerms terms, String message) {
    assertThatThrownBy(() -> rule.check(terms))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage(message);
  }
}
