package com.banking.productservice.domain.model.enums;

/**
 * Profile of a customer within its type. A product can require one: VIP exists only for personal
 * customers and SME only for business customers.
 */
public enum CustomerProfile {
  STANDARD,
  VIP,
  SME
}
