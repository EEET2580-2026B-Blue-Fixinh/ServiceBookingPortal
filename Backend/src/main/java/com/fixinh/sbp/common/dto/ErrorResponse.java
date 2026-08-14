package com.fixinh.sbp.common.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixinh.sbp.common.exception.FieldViolation;

/**
 * The single failure body every endpoint returns (API Documentation Section 2.7).
 *
 * <p>{@code fieldErrors} is omitted entirely rather than sent as null when a failure is not a
 * validation failure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		Instant timestamp,
		String path,
		int status,
		String error,
		String message,
		List<FieldViolation> fieldErrors,
		String traceId) {
}
