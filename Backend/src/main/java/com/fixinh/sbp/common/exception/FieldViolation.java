package com.fixinh.sbp.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One failing field, with everything the frontend needs to render a message without
 * inventing wording (API Documentation Section 2.7, Req 3.1.M4).
 *
 * @param field   the offending request field, e.g. {@code password} or {@code address.ward}
 * @param code    an Appendix C code, e.g. {@code PASSWORD_MISSING_DIGIT}
 * @param message what is wrong, in words
 * @param cause   why it is wrong for <em>this</em> value, e.g. "Yours has 6."
 * @param example a value that would pass
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldViolation(
		String field,
		String code,
		String message,
		String cause,
		String example) {

	/** Builds a violation from an Appendix C code, supplying only the value-specific cause. */
	public static FieldViolation of(String field, ErrorCode code, String cause) {
		return new FieldViolation(field, code.name(), code.getMessage(), cause, code.getExample());
	}
}
