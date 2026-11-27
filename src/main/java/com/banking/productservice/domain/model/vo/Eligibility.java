package com.banking.productservice.domain.model.vo;

import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.enums.CustomerProfile;
import com.banking.productservice.domain.model.enums.CustomerType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Who can open a product.
 *
 * @param customerTypes customer types that can open it, at least one
 * @param requiredProfile profile the customer must have, {@code null} when any is accepted; every
 *     accepted customer type must support it
 * @param requiresCreditCard whether the customer must hold an active credit card when the account
 *     is opened
 */
public record Eligibility(
    Set<CustomerType> customerTypes, CustomerProfile requiredProfile, boolean requiresCreditCard) {

  /**
   * Requires at least one customer type and, when a profile is required, that every accepted type
   * supports it. The types are kept as an unmodifiable copy in enum order.
   */
  public Eligibility {
    if (customerTypes == null
        || customerTypes.isEmpty()
        || customerTypes.stream().anyMatch(Objects::isNull)) {
      throw new InvalidProductException("At least one customer type is required.");
    }
    customerTypes = Collections.unmodifiableSet(EnumSet.copyOf(customerTypes));

    if (requiredProfile != null) {
      for (CustomerType type : customerTypes) {
        if (!type.supports(requiredProfile)) {
          throw new InvalidProductException(
              "Customer profile "
                  + requiredProfile
                  + " is not available for customer type "
                  + type
                  + ".");
        }
      }
    }
  }

  /** Whether a customer of the given type and profile can open the product. */
  public boolean accepts(CustomerType type, CustomerProfile profile) {
    return customerTypes.contains(type) && (requiredProfile == null || requiredProfile == profile);
  }
}
