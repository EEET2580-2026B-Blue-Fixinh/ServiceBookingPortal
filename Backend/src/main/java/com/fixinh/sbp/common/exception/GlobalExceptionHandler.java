package com.fixinh.sbp.common.exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

	/** Anything unplanned. The traceId in the body is the key into the server log. */
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
