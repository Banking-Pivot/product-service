package com.banking.productservice.domain.model;

import com.banking.productservice.domain.model.vo.ProductTerms;

/**
 * Fixed term: free of maintenance fee, one movement per month on its operation day. That single
 * movement is part of the family, not a term, so no limit is stored.
 */
public final class FixedTermRule implements FamilyRule {

  @Override
  public void check(ProductTerms terms) {
    FamilyRule.requires(
        terms.operationDayOfMonth(), "A FIXED_TERM product requires an operation day of month.");
    FamilyRule.forbids(
        terms.monthlyMovementLimit(),
        "A FIXED_TERM product cannot have a monthly movement limit: it allows one movement per"
            + " month.");
    FamilyRule.forbids(
        terms.monthlyMaintenanceFee(), "A FIXED_TERM product is exempt from the maintenance fee.");
    FamilyRule.forbids(
        terms.minimumDailyAverageBalance(),
        "A FIXED_TERM product cannot have a minimum daily average balance.");
  }
}
