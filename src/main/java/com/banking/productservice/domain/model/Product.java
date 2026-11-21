package com.banking.productservice.domain.model;

import com.banking.productservice.domain.exception.InvalidProductException;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Product aggregate: one entry of the catalog of account products.
 *
 * <p>Code and family are immutable. Name, eligibility and terms can be edited, and only while the
 * product is active. Retirement is logical: the record and its code are kept, because the accounts
 * opened with the product keep referring to it.
 *
 * <p>{@code version} is the persisted revision the aggregate was loaded at. The domain never
 * increments it; the persistence adapter does so atomically when it writes a change.
 */
@Getter
public final class Product {

  public static final int MAX_NAME_LENGTH = 120;

  /** Uppercase letters, digits and underscores, starting with a letter: 2 to 40 characters. */
  public static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

  private final String code;
  private final ProductFamily family;
  private final Instant createdAt;
  private final long version;

  private String name;
  private Eligibility eligibility;
  private ProductTerms terms;
  private ProductStatus status;
  private Instant updatedAt;

  private Product(
      String code,
      String name,
      ProductFamily family,
      Eligibility eligibility,
      ProductTerms terms,
      ProductStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    if (code == null || !CODE_PATTERN.matcher(code).matches()) {
      throw new InvalidProductException("Product code is invalid.");
    }
    if (family == null) {
      throw new InvalidProductException("Product family is required.");
    }
    this.code = code;
    this.family = family;
    this.status = Objects.requireNonNull(status, "Product status is required.");
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp is required.");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp is required.");

    if (updatedAt.isBefore(createdAt)) {
      throw new InvalidProductException("Update timestamp cannot precede creation timestamp.");
    }
    if (version < 0) {
      throw new IllegalArgumentException("Version must not be negative.");
    }

    this.name = normalizeName(name);
    this.eligibility = requireEligibility(eligibility);
    this.terms = requireTerms(terms);
    this.version = version;
  }

  /**
   * Adds a product to the catalog. New products are always ACTIVE and start at revision 0 with
   * equal creation and update timestamps.
   */
  public static Product create(
      String code,
      String name,
      ProductFamily family,
      Eligibility eligibility,
      ProductTerms terms,
      Instant now) {
    return new Product(code, name, family, eligibility, terms, ProductStatus.ACTIVE, now, now, 0L);
  }

  /** Rebuilds a product from persisted state, validating the same invariants. */
  public static Product restore(
      String code,
      String name,
      ProductFamily family,
      Eligibility eligibility,
      ProductTerms terms,
      ProductStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Product(
        code, name, family, eligibility, terms, status, createdAt, updatedAt, version);
  }

  public boolean isActive() {
    return status == ProductStatus.ACTIVE;
  }

  /**
   * Replaces the editable information of an active product.
   *
   * @return {@code true} if name, eligibility or terms changed; {@code false} if the requested
   *     values were already in place, in which case nothing changes.
   * @throws IllegalStateException if the product is retired; callers must check {@link #isActive()}
   *     first and report the conflict themselves.
   */
  public boolean update(
      String newName, Eligibility newEligibility, ProductTerms newTerms, Instant now) {
    if (!isActive()) {
      throw new IllegalStateException("Retired products cannot be updated.");
    }

    // Validate every value before modifying the current state.
    String normalizedName = normalizeName(newName);
    requireEligibility(newEligibility);
    requireTerms(newTerms);

    if (name.equals(normalizedName)
        && eligibility.equals(newEligibility)
        && terms.equals(newTerms)) {
      return false;
    }

    validateTimestamp(now);

    this.name = normalizedName;
    this.eligibility = newEligibility;
    this.terms = newTerms;
    this.updatedAt = now;
    return true;
  }

  /**
   * Retires the product while preserving its code and terms.
   *
   * @return {@code true} if the product was active; {@code false} if it was already retired, in
   *     which case nothing changes.
   */
  public boolean retire(Instant now) {
    if (!isActive()) {
      return false;
    }

    validateTimestamp(now);

    this.status = ProductStatus.RETIRED;
    this.updatedAt = now;
    return true;
  }

  private static Eligibility requireEligibility(Eligibility candidate) {
    if (candidate == null) {
      throw new InvalidProductException("Eligibility is required.");
    }
    return candidate;
  }

  /** The family decides which optional terms the product must and must not define. */
  private ProductTerms requireTerms(ProductTerms candidate) {
    if (candidate == null) {
      throw new InvalidProductException("Terms are required.");
    }
    family.rule().check(candidate);
    return candidate;
  }

  private void validateTimestamp(Instant timestamp) {
    if (timestamp == null || timestamp.isBefore(updatedAt)) {
      throw new InvalidProductException("Operation timestamp cannot precede the last update.");
    }
  }

  private static String normalizeName(String value) {
    String normalized = value == null ? null : value.strip();

    if (normalized == null || normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
      throw new InvalidProductException("Name is invalid.");
    }
    return normalized;
  }
}
