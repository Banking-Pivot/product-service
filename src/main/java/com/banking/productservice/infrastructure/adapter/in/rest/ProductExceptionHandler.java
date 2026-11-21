package com.banking.productservice.infrastructure.adapter.in.rest;

import com.banking.productservice.application.exception.DuplicateProductCodeException;
import com.banking.productservice.application.exception.ProductConcurrentModificationException;
import com.banking.productservice.application.exception.ProductNotFoundException;
import com.banking.productservice.application.exception.RetiredProductException;
import com.banking.productservice.domain.exception.InvalidProductException;
import com.banking.productservice.infrastructure.adapter.in.rest.generated.model.ErrorResponse;
import com.banking.productservice.infrastructure.correlation.CorrelationId;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * Maps every failure to the contract's ErrorResponse with a stable code.
 *
 * <p>Also receives errors raised before the delegate runs (415 from request mapping, 428 from a
 * missing If-Match, 400 from parameter validation), because WebFlux routes dispatch errors to
 * {@code @ExceptionHandler} methods. Messages never echo request values.
 */
@Slf4j
@RestControllerAdvice
public class ProductExceptionHandler {

  private final Clock clock;

  public ProductExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** No product has the requested code: 404 PRODUCT_NOT_FOUND. */
  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ProductNotFoundException exception, ServerWebExchange exchange) {
    return error(
        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "The product does not exist.", exchange);
  }

  /**
   * Creation with a code that an active or retired product already uses: 409
   * PRODUCT_CODE_ALREADY_EXISTS.
   */
  @ExceptionHandler(DuplicateProductCodeException.class)
  public ResponseEntity<ErrorResponse> handleDuplicate(
      DuplicateProductCodeException exception, ServerWebExchange exchange) {
    return error(
        HttpStatus.CONFLICT,
        "PRODUCT_CODE_ALREADY_EXISTS",
        "A product with this code already exists.",
        exchange);
  }

  /** Update of a retired product: 409 PRODUCT_RETIRED. */
  @ExceptionHandler(RetiredProductException.class)
  public ResponseEntity<ErrorResponse> handleRetired(
      RetiredProductException exception, ServerWebExchange exchange) {
    return error(
        HttpStatus.CONFLICT, "PRODUCT_RETIRED", "Retired products cannot be updated.", exchange);
  }

  /**
   * The revision in If-Match is no longer the stored one: 412 PRODUCT_VERSION_CONFLICT. The client
   * must read the product again.
   */
  @ExceptionHandler(ProductConcurrentModificationException.class)
  public ResponseEntity<ErrorResponse> handleVersionConflict(
      ProductConcurrentModificationException exception, ServerWebExchange exchange) {
    return error(
        HttpStatus.PRECONDITION_FAILED,
        "PRODUCT_VERSION_CONFLICT",
        "The product changed. Read it again before retrying.",
        exchange);
  }

  /**
   * Values that break a catalog rule, such as terms the product family does not allow: 422
   * PRODUCT_RULE_VIOLATION with the message of the rule.
   */
  @ExceptionHandler(InvalidProductException.class)
  public ResponseEntity<ErrorResponse> handleBusinessRule(
      InvalidProductException exception, ServerWebExchange exchange) {
    // Domain messages name fields and enum values only, never the submitted text.
    return error(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "PRODUCT_RULE_VIOLATION",
        exception.getMessage(),
        exchange);
  }

  /**
   * Missing If-Match header: 428 IF_MATCH_REQUIRED. Any other missing request value: 400
   * INVALID_REQUEST.
   */
  @ExceptionHandler(MissingRequestValueException.class)
  public ResponseEntity<ErrorResponse> handleMissingValue(
      MissingRequestValueException exception, ServerWebExchange exchange) {
    if (HttpHeaders.IF_MATCH.equalsIgnoreCase(exception.getName())) {
      return error(
          HttpStatus.PRECONDITION_REQUIRED,
          "IF_MATCH_REQUIRED",
          "If-Match header is required. Send the ETag from your last read.",
          exchange);
    }
    return invalidRequest("The request is missing a required value.", exchange);
  }

  /**
   * Body that fails the field constraints of the contract: 400 INVALID_REQUEST. The message names
   * the fields, never their values.
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<ErrorResponse> handleInvalidBody(
      WebExchangeBindException exception, ServerWebExchange exchange) {
    String fields =
        exception.getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
    return invalidRequest("Invalid or missing fields: " + fields + ".", exchange);
  }

  /**
   * Request that cannot be read, such as malformed JSON or an unknown field: 400 INVALID_REQUEST.
   */
  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<ErrorResponse> handleInvalidInput(
      ServerWebInputException exception, ServerWebExchange exchange) {
    return invalidRequest(
        "The request is malformed or contains invalid, unknown or missing fields.", exchange);
  }

  /**
   * Parameter that fails the constraints of the contract: 400 INVALID_IF_MATCH when it is the
   * If-Match header, 400 INVALID_REQUEST otherwise.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception, ServerWebExchange exchange) {
    boolean ifMatch =
        exception.getConstraintViolations().stream()
            .anyMatch(violation -> violation.getPropertyPath().toString().endsWith(".ifMatch"));
    return ifMatch
        ? invalidIfMatch(exchange)
        : invalidRequest("The request contains invalid parameters.", exchange);
  }

  /**
   * Parameter rejected by Spring's method validation: 400 INVALID_IF_MATCH when it is the If-Match
   * header, 400 INVALID_REQUEST otherwise. A rejected return value is a server fault: 500
   * INTERNAL_ERROR.
   */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleMethodValidation(
      HandlerMethodValidationException exception, ServerWebExchange exchange) {
    if (exception.isForReturnValue()) {
      return unexpected(exception, exchange);
    }
    boolean ifMatch =
        exception.getParameterValidationResults().stream()
            .anyMatch(result -> isIfMatchHeader(result.getMethodParameter()));
    return ifMatch
        ? invalidIfMatch(exchange)
        : invalidRequest("The request contains invalid parameters.", exchange);
  }

  /** Body that is not application/json: 415 UNSUPPORTED_MEDIA_TYPE. */
  @ExceptionHandler(UnsupportedMediaTypeStatusException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
      UnsupportedMediaTypeStatusException exception, ServerWebExchange exchange) {
    return error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Request body must use application/json.",
        exchange);
  }

  /**
   * Any other HTTP error raised by WebFlux keeps its status and uses the status name as code, for
   * example 405 METHOD_NOT_ALLOWED. A 5xx or unrecognized status becomes 500 INTERNAL_ERROR.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleHttpError(
      ResponseStatusException exception, ServerWebExchange exchange) {
    HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
    if (status == null || status.is5xxServerError()) {
      return unexpected(exception, exchange);
    }
    return error(status, status.name(), status.getReasonPhrase() + ".", exchange);
  }

  /**
   * Any other failure: 500 INTERNAL_ERROR. Only the correlation ID and the exception type are
   * logged.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(
      Exception exception, ServerWebExchange exchange) {
    return unexpected(exception, exchange);
  }

  private ResponseEntity<ErrorResponse> unexpected(
      Exception exception, ServerWebExchange exchange) {
    // Log the request and the exception type only, never payloads or database messages.
    log.error(
        "Unhandled product request failure. corrId={}, exceptionType={}",
        CorrelationId.of(exchange),
        exception.getClass().getName());
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "The request could not be completed.",
        exchange);
  }

  private ResponseEntity<ErrorResponse> invalidIfMatch(ServerWebExchange exchange) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_IF_MATCH",
        "If-Match must be exactly one quoted numeric ETag, for example \"3\".",
        exchange);
  }

  private ResponseEntity<ErrorResponse> invalidRequest(String message, ServerWebExchange exchange) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, exchange);
  }

  private static boolean isIfMatchHeader(MethodParameter parameter) {
    RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
    return header != null
        && HttpHeaders.IF_MATCH.equalsIgnoreCase(
            header.value().isEmpty() ? header.name() : header.value());
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatus status, String code, String message, ServerWebExchange exchange) {
    ErrorResponse response =
        new ErrorResponse()
            .code(code)
            .message(message)
            .timestamp(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
            .path(exchange.getRequest().getPath().value())
            .traceId(CorrelationId.of(exchange));

    return ResponseEntity.status(status).body(response);
  }
}
