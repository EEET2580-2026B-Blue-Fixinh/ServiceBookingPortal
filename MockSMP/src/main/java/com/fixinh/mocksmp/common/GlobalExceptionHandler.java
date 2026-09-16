package com.fixinh.mocksmp.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fixinh.mocksmp.staff.DuplicateEmailException;
import com.fixinh.mocksmp.staff.StaffNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiError> onDuplicateEmail(DuplicateEmailException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("EMAIL_ALREADY_REGISTERED", ex.getMessage()));
	}

	@ExceptionHandler(StaffNotFoundException.class)
	public ResponseEntity<ApiError> onStaffNotFound(StaffNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiError("STAFF_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> onValidationFailed(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("One or more fields are invalid.");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiError("VALIDATION_FAILED", detail));
	}
}
