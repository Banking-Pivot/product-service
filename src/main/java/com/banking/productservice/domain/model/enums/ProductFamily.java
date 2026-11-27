package com.banking.productservice.domain.model.enums;

import com.banking.productservice.domain.model.CheckingRule;
import com.banking.productservice.domain.model.FamilyRule;
import com.banking.productservice.domain.model.FixedTermRule;
import com.banking.productservice.domain.model.SavingsRule;

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
