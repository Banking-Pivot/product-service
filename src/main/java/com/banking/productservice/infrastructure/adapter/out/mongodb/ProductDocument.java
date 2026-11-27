package com.banking.productservice.infrastructure.adapter.out.mongodb;

import com.banking.productservice.domain.model.enums.CustomerProfile;
import com.banking.productservice.domain.model.enums.ProductFamily;
import com.banking.productservice.domain.model.enums.ProductStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.Decimal128;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistence shape of a product in {@code products_db.products}.
 *
 * <p>The product code is the document {@code _id}, so MongoDB itself guarantees that a code
 * identifies one product, retired ones included, and no extra index is needed. Amounts are stored
 * as Decimal128 by currency code, which keeps the two decimals. Terms that do not apply to the
 * product are absent from the document. {@code version} is the optimistic-locking revision exposed
 * as ETag.
 */
@Getter
@AllArgsConstructor
@Document(collection = ProductDocument.COLLECTION)
public class ProductDocument {

  public static final String COLLECTION = "products";

  static final String FIELD_CODE = "code";
  static final String FIELD_NAME = "name";
  static final String FIELD_FAMILY = "family";
  static final String FIELD_STATUS = "status";
  static final String FIELD_CUSTOMER_TYPES = "customerTypes";
  static final String FIELD_REQUIRED_PROFILE = "requiredProfile";
  static final String FIELD_REQUIRES_CREDIT_CARD = "requiresCreditCard";
  static final String FIELD_OPENING_MINIMUM = "openingMinimum";
  static final String FIELD_FREE_MONTHLY_MOVEMENTS = "freeMonthlyMovements";
  static final String FIELD_MOVEMENT_FEE = "movementFee";
  static final String FIELD_MONTHLY_MOVEMENT_LIMIT = "monthlyMovementLimit";
  static final String FIELD_MONTHLY_MAINTENANCE_FEE = "monthlyMaintenanceFee";
  static final String FIELD_MINIMUM_DAILY_AVERAGE_BALANCE = "minimumDailyAverageBalance";
  static final String FIELD_OPERATION_DAY_OF_MONTH = "operationDayOfMonth";
  static final String FIELD_UPDATED_AT = "updatedAt";
  static final String FIELD_VERSION = "version";

  @Id private String code;
  private String name;
  private ProductFamily family;
  private ProductStatus status;
  private List<String> customerTypes;
  private CustomerProfile requiredProfile;
  private boolean requiresCreditCard;
  private Map<String, Decimal128> openingMinimum;
  private int freeMonthlyMovements;
  private Map<String, Decimal128> movementFee;
  private Integer monthlyMovementLimit;
  private Map<String, Decimal128> monthlyMaintenanceFee;
  private Map<String, Decimal128> minimumDailyAverageBalance;
  private Integer operationDayOfMonth;
  private Instant createdAt;
  private Instant updatedAt;
  @Version private Long version;
}
