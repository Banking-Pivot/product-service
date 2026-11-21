package com.banking.productservice.domain.model;

/** Customer classification, with the profiles each type can have. */
public enum CustomerType {
  PERSONAL,
  BUSINESS;

  /** PERSONAL supports STANDARD and VIP; BUSINESS supports STANDARD and SME. */
  public boolean supports(CustomerProfile profile) {
    return switch (this) {
      case PERSONAL -> profile == CustomerProfile.STANDARD || profile == CustomerProfile.VIP;
      case BUSINESS -> profile == CustomerProfile.STANDARD || profile == CustomerProfile.SME;
    };
  }
}
