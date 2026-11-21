package com.banking.productservice.infrastructure.adapter.out.mongodb;

import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.domain.model.CurrencyCode;
import com.banking.productservice.domain.model.CustomerType;
import com.banking.productservice.domain.model.Eligibility;
import com.banking.productservice.domain.model.Product;
import com.banking.productservice.domain.model.ProductTerms;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.types.Decimal128;
import org.springframework.stereotype.Component;

/**
 * Converts between the product aggregate, its document in the {@code products} collection and the
 * read view. Customer types are stored by name and amounts as Decimal128 by currency code.
 */
@Component
public class ProductMongoMapper {

  /**
   * Stored shape of the aggregate, with eligibility and terms as top-level fields. Terms that do
   * not apply stay {@code null} and are left out of the document.
   */
  public ProductDocument toDocument(Product product) {
    Eligibility eligibility = product.getEligibility();
    ProductTerms terms = product.getTerms();
    return new ProductDocument(
        product.getCode(),
        product.getName(),
        product.getFamily(),
        product.getStatus(),
        toStored(eligibility),
        eligibility.requiredProfile(),
        eligibility.requiresCreditCard(),
        toStored(terms.openingMinimum()),
        terms.freeMonthlyMovements(),
        toStored(terms.movementFee()),
        terms.monthlyMovementLimit(),
        toStored(terms.monthlyMaintenanceFee()),
        toStored(terms.minimumDailyAverageBalance()),
        terms.operationDayOfMonth(),
        product.getCreatedAt(),
        product.getUpdatedAt(),
        product.getVersion());
  }

  /** Write side: rebuilds the aggregate so its rules apply to the next change. */
  public Product toDomain(ProductDocument document) {
    return Product.restore(
        document.getCode(),
        document.getName(),
        document.getFamily(),
        eligibilityOf(document),
        termsOf(document),
        document.getStatus(),
        document.getCreatedAt(),
        document.getUpdatedAt(),
        document.getVersion());
  }

  /** Read side: projects stored data straight into the view, without the aggregate. */
  public ProductView toView(ProductDocument document) {
    return new ProductView(
        document.getCode(),
        document.getName(),
        document.getFamily(),
        eligibilityOf(document),
        termsOf(document),
        document.getStatus(),
        document.getCreatedAt(),
        document.getUpdatedAt(),
        document.getVersion());
  }

  /** Customer type names in enum order, so equal eligibilities are stored equally. */
  List<String> toStored(Eligibility eligibility) {
    return eligibility.customerTypes().stream().map(Enum::name).toList();
  }

  /** Amounts by currency code, or {@code null} when the term does not apply. */
  Map<String, Decimal128> toStored(Map<CurrencyCode, BigDecimal> amounts) {
    if (amounts == null) {
      return null;
    }
    Map<String, Decimal128> stored = new LinkedHashMap<>();
    amounts.forEach((currency, amount) -> stored.put(currency.name(), new Decimal128(amount)));
    return stored;
  }

  private static Eligibility eligibilityOf(ProductDocument document) {
    return new Eligibility(
        document.getCustomerTypes().stream().map(CustomerType::valueOf).collect(Collectors.toSet()),
        document.getRequiredProfile(),
        document.isRequiresCreditCard());
  }

  private static ProductTerms termsOf(ProductDocument document) {
    return new ProductTerms(
        toAmounts(document.getOpeningMinimum()),
        document.getFreeMonthlyMovements(),
        toAmounts(document.getMovementFee()),
        document.getMonthlyMovementLimit(),
        toAmounts(document.getMonthlyMaintenanceFee()),
        toAmounts(document.getMinimumDailyAverageBalance()),
        document.getOperationDayOfMonth());
  }

  private static Map<CurrencyCode, BigDecimal> toAmounts(Map<String, Decimal128> stored) {
    if (stored == null) {
      return null;
    }
    Map<CurrencyCode, BigDecimal> amounts = new EnumMap<>(CurrencyCode.class);
    stored.forEach(
        (currency, amount) ->
            amounts.put(CurrencyCode.valueOf(currency), amount.bigDecimalValue()));
    return amounts;
  }
}
