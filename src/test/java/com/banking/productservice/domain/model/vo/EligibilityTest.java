package com.banking.productservice.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.enums.CustomerProfile;
import com.banking.productservice.domain.model.enums.CustomerType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EligibilityTest {

  @Test
  void atLeastOneCustomerTypeIsRequired() {
    assertThatThrownBy(() -> new Eligibility(Set.of(), null, false))
        .isInstanceOf(InvalidProductException.class);
    assertThatThrownBy(() -> new Eligibility(null, null, false))
        .isInstanceOf(InvalidProductException.class);
    assertThatThrownBy(
            () -> new Eligibility(new HashSet<>(Collections.singleton(null)), null, false))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void requiredProfileMustBeAvailableForEveryAcceptedCustomerType() {
    assertThatThrownBy(
            () -> new Eligibility(Set.of(CustomerType.BUSINESS), CustomerProfile.VIP, false))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("Customer profile VIP is not available for customer type BUSINESS.");
    assertThatThrownBy(
            () -> new Eligibility(Set.of(CustomerType.PERSONAL), CustomerProfile.SME, false))
        .isInstanceOf(InvalidProductException.class);
    assertThatThrownBy(
            () ->
                new Eligibility(
                    Set.of(CustomerType.PERSONAL, CustomerType.BUSINESS),
                    CustomerProfile.VIP,
                    false))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void standardProfileIsAvailableForBothCustomerTypes() {
    Eligibility eligibility =
        new Eligibility(
            Set.of(CustomerType.PERSONAL, CustomerType.BUSINESS), CustomerProfile.STANDARD, false);

    assertThat(eligibility.accepts(CustomerType.BUSINESS, CustomerProfile.STANDARD)).isTrue();
    assertThat(eligibility.accepts(CustomerType.BUSINESS, CustomerProfile.SME)).isFalse();
  }

  @Test
  void withoutRequiredProfileAnyProfileOfAnAcceptedTypeIsAccepted() {
    Eligibility eligibility = new Eligibility(Set.of(CustomerType.PERSONAL), null, false);

    assertThat(eligibility.accepts(CustomerType.PERSONAL, CustomerProfile.STANDARD)).isTrue();
    assertThat(eligibility.accepts(CustomerType.PERSONAL, CustomerProfile.VIP)).isTrue();
    assertThat(eligibility.accepts(CustomerType.BUSINESS, CustomerProfile.STANDARD)).isFalse();
  }

  @Test
  void vipSavingsAcceptsOnlyPersonalVipCustomers() {
    Eligibility eligibility =
        new Eligibility(Set.of(CustomerType.PERSONAL), CustomerProfile.VIP, true);

    assertThat(eligibility.accepts(CustomerType.PERSONAL, CustomerProfile.VIP)).isTrue();
    assertThat(eligibility.accepts(CustomerType.PERSONAL, CustomerProfile.STANDARD)).isFalse();
  }

  @Test
  void customerTypesCannotBeChangedFromOutside() {
    Set<CustomerType> types = new HashSet<>(Set.of(CustomerType.PERSONAL));
    Eligibility eligibility = new Eligibility(types, null, false);

    types.add(CustomerType.BUSINESS);

    assertThat(eligibility.customerTypes()).containsExactly(CustomerType.PERSONAL);
    assertThatThrownBy(() -> eligibility.customerTypes().add(CustomerType.BUSINESS))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
