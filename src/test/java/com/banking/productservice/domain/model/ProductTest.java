package com.banking.productservice.domain.model;

import static com.banking.productservice.support.Products.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.domain.model.enums.CustomerProfile;
import com.banking.productservice.domain.model.enums.CustomerType;
import com.banking.productservice.domain.model.enums.ProductFamily;
import com.banking.productservice.domain.model.enums.ProductStatus;
import com.banking.productservice.domain.model.vo.Eligibility;
import com.banking.productservice.domain.model.vo.ProductTerms;
import com.banking.productservice.support.Products;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

  private static final Instant T1 = T0.plusSeconds(60);

  @Test
  void newProductIsActiveAtRevisionZeroWithEqualTimestamps() {
    Product product =
        Product.create(
            "SAVINGS",
            "  Savings account ",
            ProductFamily.SAVINGS,
            Products.personal(),
            Products.savingsTerms(),
            T0);

    assertThat(product.getCode()).isEqualTo("SAVINGS");
    assertThat(product.getName()).isEqualTo("Savings account");
    assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.isActive()).isTrue();
    assertThat(product.getVersion()).isZero();
    assertThat(product.getCreatedAt()).isEqualTo(T0);
    assertThat(product.getUpdatedAt()).isEqualTo(T0);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "S",
        "savings",
        "1SAVINGS",
        "_SAVINGS",
        "VIP-SAVINGS",
        "VIP SAVINGS",
        "A0123456789012345678901234567890123456789"
      })
  void invalidCodeIsRejected(String code) {
    assertThatThrownBy(
            () ->
                Product.create(
                    code,
                    "Savings account",
                    ProductFamily.SAVINGS,
                    Products.personal(),
                    Products.savingsTerms(),
                    T0))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("Product code is invalid.");
  }

  @Test
  void codeCanHaveFortyCharactersDigitsAndUnderscores() {
    String code = "A" + "9_".repeat(19) + "Z";

    assertThat(
            Product.create(
                    code,
                    "Savings account",
                    ProductFamily.SAVINGS,
                    Products.personal(),
                    Products.savingsTerms(),
                    T0)
                .getCode())
        .hasSize(40);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void blankNameIsRejected(String name) {
    assertThatThrownBy(
            () ->
                Product.create(
                    "SAVINGS",
                    name,
                    ProductFamily.SAVINGS,
                    Products.personal(),
                    Products.savingsTerms(),
                    T0))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void nameLongerThan120CharactersIsRejected() {
    assertThatThrownBy(
            () ->
                Product.create(
                    "SAVINGS",
                    "x".repeat(121),
                    ProductFamily.SAVINGS,
                    Products.personal(),
                    Products.savingsTerms(),
                    T0))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void familyIsRequired() {
    assertThatThrownBy(
            () ->
                Product.create(
                    "SAVINGS",
                    "Savings account",
                    null,
                    Products.personal(),
                    Products.savingsTerms(),
                    T0))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void termsMustFollowTheFamilyOfTheProduct() {
    assertThatThrownBy(
            () ->
                Product.create(
                    "SAVINGS",
                    "Savings account",
                    ProductFamily.SAVINGS,
                    Products.personal(),
                    Products.checkingTerms(),
                    T0))
        .isInstanceOf(InvalidProductException.class)
        .hasMessage("A SAVINGS product requires a monthly movement limit.");
  }

  @Test
  void updateReplacesNameEligibilityAndTerms() {
    Product product = Products.savings("SAVINGS");
    Eligibility vipOnly = new Eligibility(Set.of(CustomerType.PERSONAL), CustomerProfile.VIP, true);
    ProductTerms newTerms =
        new ProductTerms(
            Products.amounts("500.00", "150.00"),
            8,
            Products.amounts("2.00", "0.60"),
            15,
            null,
            Products.amounts("500.00", "150.00"),
            null);

    boolean changed = product.update(" Premium savings ", vipOnly, newTerms, T1);

    assertThat(changed).isTrue();
    assertThat(product.getName()).isEqualTo("Premium savings");
    assertThat(product.getEligibility()).isEqualTo(vipOnly);
    assertThat(product.getTerms()).isEqualTo(newTerms);
    assertThat(product.getUpdatedAt()).isEqualTo(T1);
    assertThat(product.getCreatedAt()).isEqualTo(T0);
    assertThat(product.getFamily()).isEqualTo(ProductFamily.SAVINGS);
  }

  @Test
  void updateWithTheSameValuesChangesNothing() {
    Product product = Products.savings("SAVINGS");

    boolean changed =
        product.update(" Savings account ", Products.personal(), Products.savingsTerms(), T1);

    assertThat(changed).isFalse();
    assertThat(product.getUpdatedAt()).isEqualTo(T0);
  }

  @Test
  void updateThatBreaksTheFamilyRuleLeavesTheProductUntouched() {
    Product product = Products.savings("SAVINGS");

    assertThatThrownBy(
            () -> product.update("Other", Products.personal(), Products.fixedTermTerms(), T1))
        .isInstanceOf(InvalidProductException.class);

    assertThat(product.getName()).isEqualTo("Savings account");
    assertThat(product.getTerms()).isEqualTo(Products.savingsTerms());
    assertThat(product.getUpdatedAt()).isEqualTo(T0);
  }

  @Test
  void retiredProductCannotBeUpdated() {
    Product product = Products.savings("SAVINGS");
    product.retire(T1);

    assertThatThrownBy(
            () -> product.update("Other", Products.personal(), Products.savingsTerms(), T1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void retireKeepsCodeAndTermsAndIsANoOpTheSecondTime() {
    Product product = Products.savings("SAVINGS");

    assertThat(product.retire(T1)).isTrue();
    assertThat(product.getStatus()).isEqualTo(ProductStatus.RETIRED);
    assertThat(product.isActive()).isFalse();
    assertThat(product.getUpdatedAt()).isEqualTo(T1);
    assertThat(product.getTerms()).isEqualTo(Products.savingsTerms());

    assertThat(product.retire(T1.plusSeconds(60))).isFalse();
    assertThat(product.getUpdatedAt()).isEqualTo(T1);
  }

  @Test
  void changesCannotBeDatedBeforeTheLastUpdate() {
    Product product = Products.savings("SAVINGS");

    assertThatThrownBy(() -> product.retire(T0.minusSeconds(1)))
        .isInstanceOf(InvalidProductException.class);
    assertThat(product.isActive()).isTrue();
  }

  @Test
  void restoreRejectsAnUpdateTimestampBeforeTheCreation() {
    assertThatThrownBy(
            () ->
                Product.restore(
                    "SAVINGS",
                    "Savings account",
                    ProductFamily.SAVINGS,
                    Products.personal(),
                    Products.savingsTerms(),
                    ProductStatus.ACTIVE,
                    T1,
                    T0,
                    3))
        .isInstanceOf(InvalidProductException.class);
  }
}
