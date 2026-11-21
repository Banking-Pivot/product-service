package com.banking.productservice.domain.model;

/**
 * Savings: free of maintenance fee, with a hard monthly movement limit. It may require an average
 * daily balance (VIP savings) and accepts movements any day.
 */
final class SavingsRule implements FamilyRule {

  @Override
  public void check(ProductTerms terms) {
    FamilyRule.requires(
        terms.monthlyMovementLimit(), "A SAVINGS product requires a monthly movement limit.");
    FamilyRule.forbids(
        terms.monthlyMaintenanceFee(), "A SAVINGS product is exempt from the maintenance fee.");
    FamilyRule.forbids(
        terms.operationDayOfMonth(), "A SAVINGS product cannot have an operation day of month.");
  }
}
