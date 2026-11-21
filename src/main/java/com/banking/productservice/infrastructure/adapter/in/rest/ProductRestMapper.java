package com.banking.productservice.infrastructure.adapter.in.rest;

import com.banking.productservice.application.command.CreateProductCommand;
import com.banking.productservice.application.command.UpdateProductCommand;
import com.banking.productservice.application.model.ProductPage;
import com.banking.productservice.application.model.ProductView;
import com.banking.productservice.application.query.ListProductsQuery;
import com.banking.productservice.domain.model.CurrencyCode;
import com.banking.productservice.domain.model.CustomerProfile;
import com.banking.productservice.domain.model.CustomerType;
import com.banking.productservice.domain.model.Eligibility;
import com.banking.productservice.domain.model.ProductFamily;
import com.banking.productservice.domain.model.ProductStatus;
import com.banking.productservice.domain.model.ProductTerms;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.AmountByCurrency;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.CreateProductRequest;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductEligibility;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductPageResponse;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductResponse;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductTermsBody;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.UpdateProductRequest;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;

/**
 * Translates between generated REST DTOs and application records, and between revisions and strong
 * ETags ({@code "3"}). Generated enums and domain enums share names, so conversion goes through
 * {@code name()}.
 *
 * <p>Building {@link Eligibility} and {@link ProductTerms} validates them, so a request that breaks
 * a business rule fails here with {@code InvalidProductException}.
 */
@Component
public class ProductRestMapper {

  private static final Pattern STRONG_NUMERIC_ETAG = Pattern.compile("^\"([0-9]+)\"$");

  /** Command that adds the requested product to the catalog. */
  public CreateProductCommand toCommand(CreateProductRequest request) {
    return new CreateProductCommand(
        request.getCode(),
        request.getName(),
        ProductFamily.valueOf(request.getFamily().name()),
        toEligibility(request.getEligibility()),
        toTerms(request.getTerms()));
  }

  /**
   * Command that replaces the editable fields of the product, conditioned on the revision read from
   * If-Match.
   */
  public UpdateProductCommand toCommand(
      String code, long expectedVersion, UpdateProductRequest request) {
    return new UpdateProductCommand(
        code,
        request.getName(),
        toEligibility(request.getEligibility()),
        toTerms(request.getTerms()),
        expectedVersion);
  }

  /**
   * Page request for the catalog. An omitted page or size takes the contract default (0 and 20); an
   * omitted family or status means "any".
   */
  public ListProductsQuery toQuery(
      Integer page,
      Integer size,
      com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductFamily
          family,
      com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductStatus
          status) {
    return new ListProductsQuery(
        page == null ? ListProductsQuery.DEFAULT_PAGE : page,
        size == null ? ListProductsQuery.DEFAULT_SIZE : size,
        family == null ? null : ProductFamily.valueOf(family.name()),
        status == null ? null : ProductStatus.valueOf(status.name()));
  }

  /**
   * Response body of a product, with timestamps in UTC. The revision is not part of the body: it
   * travels in the ETag header.
   */
  public ProductResponse toResponse(ProductView view) {
    return new ProductResponse()
        .code(view.code())
        .name(view.name())
        .family(
            com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductFamily
                .valueOf(view.family().name()))
        .eligibility(toBody(view.eligibility()))
        .terms(toBody(view.terms()))
        .status(
            com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ProductStatus
                .valueOf(view.status().name()))
        .createdAt(view.createdAt().atOffset(ZoneOffset.UTC))
        .updatedAt(view.updatedAt().atOffset(ZoneOffset.UTC));
  }

  /** Response body of one page of products, with the page position and the totals. */
  public ProductPageResponse toPageResponse(ProductPage page) {
    return new ProductPageResponse()
        .items(page.items().stream().map(this::toResponse).toList())
        .page(page.page())
        .size(page.size())
        .totalElements(page.totalElements())
        .totalPages(page.totalPages());
  }

  /** Strong entity tag for a revision, quoted as the contract requires. */
  public String toEtag(long version) {
    return "\"" + version + "\"";
  }

  /**
   * Reads the revision from If-Match.
   *
   * @return the revision, or empty when the tag is well formed but can never match one this API
   *     issued (leading zeros such as {@code "03"}, or a value beyond the long range). Strong
   *     comparison is character by character, so {@code "03"} does not match {@code "3"}: the
   *     caller must answer 412, not 400.
   * @throws ServerWebInputException when the header is not one quoted number. The generated
   *     controller already validates the pattern; this is a guard.
   */
  public OptionalLong toExpectedVersion(String ifMatch) {
    var matcher = ifMatch == null ? null : STRONG_NUMERIC_ETAG.matcher(ifMatch);
    if (matcher == null || !matcher.matches()) {
      throw new ServerWebInputException("Malformed If-Match header.");
    }

    String digits = matcher.group(1);
    try {
      long version = Long.parseLong(digits);
      return toEtag(version).equals(ifMatch) ? OptionalLong.of(version) : OptionalLong.empty();
    } catch (NumberFormatException beyondLongRange) {
      return OptionalLong.empty();
    }
  }

  private static Eligibility toEligibility(ProductEligibility body) {
    return new Eligibility(
        body.getCustomerTypes().stream()
            .map(type -> type == null ? null : CustomerType.valueOf(type.name()))
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        body.getRequiredProfile() == null
            ? null
            : CustomerProfile.valueOf(body.getRequiredProfile().name()),
        Boolean.TRUE.equals(body.getRequiresCreditCard()));
  }

  private static ProductTerms toTerms(ProductTermsBody body) {
    return new ProductTerms(
        toAmounts(body.getOpeningMinimum()),
        body.getFreeMonthlyMovements(),
        toAmounts(body.getMovementFee()),
        body.getMonthlyMovementLimit(),
        toAmounts(body.getMonthlyMaintenanceFee()),
        toAmounts(body.getMinimumDailyAverageBalance()),
        body.getOperationDayOfMonth());
  }

  /** Amounts by currency, or {@code null} when the request omits the term. */
  private static Map<CurrencyCode, BigDecimal> toAmounts(AmountByCurrency body) {
    if (body == null) {
      return null;
    }
    Map<CurrencyCode, BigDecimal> amounts = new EnumMap<>(CurrencyCode.class);
    amounts.put(CurrencyCode.PEN, body.getPEN());
    amounts.put(CurrencyCode.USD, body.getUSD());
    return amounts;
  }

  private static ProductEligibility toBody(Eligibility eligibility) {
    return new ProductEligibility()
        .customerTypes(
            eligibility.customerTypes().stream()
                .map(
                    type ->
                        com.banking.productservice.infrastructure.adapter.in.rest.generated.model
                            .CustomerType.valueOf(type.name()))
                .collect(Collectors.toCollection(LinkedHashSet::new)))
        .requiredProfile(
            eligibility.requiredProfile() == null
                ? null
                : com.banking.productservice.infrastructure.adapter.in.rest.generated.model
                    .CustomerProfile.valueOf(eligibility.requiredProfile().name()))
        .requiresCreditCard(eligibility.requiresCreditCard());
  }

  private static ProductTermsBody toBody(ProductTerms terms) {
    return new ProductTermsBody()
        .openingMinimum(toBody(terms.openingMinimum()))
        .freeMonthlyMovements(terms.freeMonthlyMovements())
        .movementFee(toBody(terms.movementFee()))
        .monthlyMovementLimit(terms.monthlyMovementLimit())
        .monthlyMaintenanceFee(toBody(terms.monthlyMaintenanceFee()))
        .minimumDailyAverageBalance(toBody(terms.minimumDailyAverageBalance()))
        .operationDayOfMonth(terms.operationDayOfMonth());
  }

  private static AmountByCurrency toBody(Map<CurrencyCode, BigDecimal> amounts) {
    return amounts == null
        ? null
        : new AmountByCurrency()
            .PEN(amounts.get(CurrencyCode.PEN))
            .USD(amounts.get(CurrencyCode.USD));
  }
}
