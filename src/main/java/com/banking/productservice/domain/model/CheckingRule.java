package com.banking.productservice.domain.model;

/**
 * Checking: no monthly movement limit. It may charge a maintenance fee (omitted when the product is
 * exempt, as SME checking is) and accepts movements any day.
 */
final class CheckingRule implements FamilyRule {

  @Override
  public void check(ProductTerms terms) {
    FamilyRule.forbids(
        terms.monthlyMovementLimit(), "A CHECKING product cannot have a monthly movement limit.");
    FamilyRule.forbids(
        terms.minimumDailyAverageBalance(),
        "A CHECKING product cannot have a minimum daily average balance.");
    FamilyRule.forbids(
        terms.operationDayOfMonth(), "A CHECKING product cannot have an operation day of month.");
  }
}
