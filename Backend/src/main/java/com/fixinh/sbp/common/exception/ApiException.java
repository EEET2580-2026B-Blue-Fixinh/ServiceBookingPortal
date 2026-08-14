package com.fixinh.sbp.common.exception;

import java.util.List;

import lombok.Getter;

/**
 * The one exception the API throws deliberately. {@link GlobalExceptionHandler} turns it into
 * the standard error body; the HTTP status comes from the {@link ErrorCode}.
 */
@Getter
public class ApiException extends RuntimeException {

	private final transient ErrorCode errorCode;
	private final transient List<FieldViolation> fieldErrors;

	public ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage(), List.of());
	}

	public ApiException(ErrorCode errorCode, String message) {
		this(errorCode, message, List.of());
	}

	public ApiException(ErrorCode errorCode, String message, List<FieldViolation> fieldErrors) {
		super(message);
		this.errorCode = errorCode;
		this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}

	/** The 400 raised when one or more request fields break the rules in Appendix C. */
	public static ApiException validationFailed(List<FieldViolation> fieldErrors) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getMessage(),
				fieldErrors);
	}
}
