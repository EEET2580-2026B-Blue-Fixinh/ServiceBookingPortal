package com.fixinh.sbp.common.exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fixinh.sbp.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders every failure as the one error body from Section 2.7, so the frontend has a single
 * shape to handle and can branch on {@code error} / {@code fieldErrors[].code}.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
		return build(ex.getErrorCode(), ex.getMessage(), ex.getFieldErrors(), request);
	}

	/**
	 * Bean-validation failures on {@code @Valid @RequestBody} — used by /auth/login's presence
	 * checks. Registration runs its own validator so it can return the specific Appendix C codes.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {

		List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new FieldViolation(
						fieldError.getField(),
						ErrorCode.VALIDATION_FAILED.name(),
						fieldError.getDefaultMessage(),
						"The value you sent was missing or empty.",
						null))
				.toList();

		return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getMessage(), violations,
				request);
	}

	// --- Requests that never reach a controller ------------------------------
	// Each of the six below is a mistake in the request itself, and each used to be answered
	// by handleUnexpected with 500 INTERNAL_ERROR: ExceptionHandlerExceptionResolver is
	// consulted before Spring's own resolver, so a handler for Exception intercepts the MVC
	// exceptions that would otherwise have carried their proper status. The caller was told
	// the fault was ours, and the log recorded a typo'd URL at ERROR with a stack trace,
	// alongside the faults that really were ours.
	//
	// All six log at debug. Nothing here is a fault worth waking anyone for.

	/**
	 * A method this endpoint does not support.
	 *
	 * <p>{@code Allow} is not decoration: RFC 9110 requires it on a 405, and it is the only
	 * part of the answer that says what to send instead.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(
			HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

		log.debug("{} is not supported on {}", request.getMethod(), request.getRequestURI());

		ResponseEntity<ErrorResponse> response = build(ErrorCode.METHOD_NOT_ALLOWED,
				ErrorCode.METHOD_NOT_ALLOWED.getMessage(), List.of(), request);

		Set<HttpMethod> allowed = ex.getSupportedHttpMethods();
		if (allowed == null || allowed.isEmpty()) {
			return response;
		}

		return ResponseEntity.status(response.getStatusCode())
				.allow(allowed.toArray(HttpMethod[]::new))
				.body(response.getBody());
	}

	/**
	 * A path that maps to nothing.
	 *
	 * <p>Its own code rather than one of the resource 404s. "This path does not exist" and
	 * "the booking you asked for is not there" are different answers, and a client that
	 * cannot tell them apart retries the wrong one.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoEndpoint(HttpServletRequest request) {
		log.debug("No endpoint at {} {}", request.getMethod(), request.getRequestURI());

		return build(ErrorCode.ENDPOINT_NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND.getMessage(),
				List.of(), request);
	}

	/**
	 * A body the JSON parser could not read.
	 *
	 * <p>No {@code fieldErrors}, because parsing stopped before there were fields — and
	 * nothing from the parser's own message either. Which byte offset upset Jackson is not
	 * something a caller can act on, and that message quotes the input it was reading, which
	 * on {@code /auth/login} is a password.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpServletRequest request) {
		log.debug("Unreadable body on {} {}", request.getMethod(), request.getRequestURI());

		return build(ErrorCode.REQUEST_MALFORMED, ErrorCode.REQUEST_MALFORMED.getMessage(),
				List.of(), request);
	}

	/** A {@code Content-Type} the endpoint does not accept. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
		log.debug("Unsupported Content-Type on {} {}", request.getMethod(),
				request.getRequestURI());

		return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
				ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), List.of(), request);
	}

	/** A required query parameter that was not sent. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(
			MissingServletRequestParameterException ex, HttpServletRequest request) {

		return parameterFailure(ex.getParameterName(), "It was not sent.", request);
	}

	/** A required multipart part that was not sent — §7.4's evidence upload. */
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex,
			HttpServletRequest request) {

		return parameterFailure(ex.getRequestPartName(), "It was not sent.", request);
	}

	/**
	 * A parameter that was sent but cannot be read as the type the endpoint declares —
	 * {@code ?page=first} against an {@code Integer}.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleParameterTypeMismatch(
			MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

		// The required type is deliberately not quoted back. "Must be an Integer" describes our
		// signature rather than their mistake, and the parameter's name is the part they can
		// act on. The value itself is not echoed either: it came from the caller, but a
		// response that repeats request input is a habit worth not forming.
		return parameterFailure(ex.getName(), "The value sent could not be read.", request);
	}

	/**
	 * What the three parameter failures above have in common: 400 with the parameter named in
	 * {@code fieldErrors}.
	 *
	 * <p>No new top-level code for any of them. Appendix B already defines
	 * {@code VALIDATION_FAILED} as one or more fields breaking a rule with the details in
	 * {@code fieldErrors}, and a query parameter is a field in exactly that sense — which also
	 * means the frontend handles these with the branch it already has.
	 */
	private ResponseEntity<ErrorResponse> parameterFailure(String parameter, String cause,
			HttpServletRequest request) {

		log.debug("Parameter {} rejected on {} {}", parameter, request.getMethod(),
				request.getRequestURI());

		FieldViolation violation = new FieldViolation(parameter,
				ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.getMessage(),
				cause, null);

		return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getMessage(),
				List.of(violation), request);
	}

	/**
	 * A multipart request larger than {@code spring.servlet.multipart.*} allows.
	 *
	 * <p>Container-level, so it is raised before any controller or validator sees the request —
	 * without this it would land in {@link #handleUnexpected} and a five-and-a-half megabyte
	 * photograph would come back as 500, not the 413 §7.4 promises. The configured cap sits
	 * deliberately above the 5 MB rule so {@code BookingCancelValidator} normally answers first
	 * with a message naming the actual limit; this is the backstop for a request too large to
	 * reach it at all.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
			HttpServletRequest request) {

		return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getMessage(), List.of(),
				request);
	}

	/**
	 * Anything unplanned. The traceId in the body is the key into the server log.
	 *
	 * <p>Last resort, and only that: Spring picks the most specific handler, so everything
	 * above reaches its own method and what arrives here is a genuine fault. That is what
	 * makes the ERROR log line worth reading.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		ResponseEntity<ErrorResponse> response = build(ErrorCode.INTERNAL_ERROR,
				ErrorCode.INTERNAL_ERROR.getMessage(), List.of(), request);

		ErrorResponse body = response.getBody();
		log.error("Unhandled exception [traceId={}] on {} {}",
				body == null ? "unknown" : body.traceId(),
				request.getMethod(), request.getRequestURI(), ex);

		return response;
	}

	private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message,
			List<FieldViolation> fieldErrors, HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now().truncatedTo(ChronoUnit.SECONDS),
				request.getRequestURI(),
				errorCode.getHttpStatus().value(),
				errorCode.name(),
				message,
				fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors,
				UUID.randomUUID().toString());

		return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
	}
}
