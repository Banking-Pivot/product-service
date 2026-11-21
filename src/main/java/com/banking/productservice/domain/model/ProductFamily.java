package com.banking.productservice.domain.model;

/** Account family of a product. Each family brings the rule its terms must follow. */
public enum ProductFamily {
  SAVINGS(new SavingsRule()),
  CHECKING(new CheckingRule()),
  FIXED_TERM(new FixedTermRule());

  private final FamilyRule rule;

  ProductFamily(FamilyRule rule) {
    this.rule = rule;
  }

  /** Rule the terms of a product of this family must follow. */
  public FamilyRule rule() {
    return rule;
  }
}
