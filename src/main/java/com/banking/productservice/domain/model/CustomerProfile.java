package com.banking.productservice.domain.model;

/**
 * Profile of a customer within its type. A product can require one: VIP exists only for personal
 * customers and SME only for business customers.
 */
public enum CustomerProfile {
  STANDARD,
  VIP,
  SME
}
