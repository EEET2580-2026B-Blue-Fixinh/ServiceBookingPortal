package com.fixinh.sbp.modules.auth.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.exception.FieldViolation;
import com.fixinh.sbp.common.validation.ProfileFieldValidator;
import com.fixinh.sbp.modules.auth.dto.internal.RegisterCommand;

import lombok.RequiredArgsConstructor;

/**
 * Registration's field rules (Appendix C, Reqs 3.1.M1–3.1.M4).
 *
 * <p>Every rule is checked and <em>all</em> failures are returned together, because the contract
 * is that "every failing field appears in fieldErrors" — failing fast on the first would make the
 * customer fix one thing per round trip.
 *
 * <p>The same rules run in the browser for speed. This copy is the authoritative one. Name,
 * phone and address rules are shared with profile updates (Section 6.2) via
 * {@link ProfileFieldValidator}; only password and email are registration-only.
 */
@Component
@RequiredArgsConstructor
public class RegistrationValidator {

	private static final int PASSWORD_MIN_LENGTH = 8;
	private static final int EMAIL_MAX_LENGTH = 255;

	/** Characters an email may not contain, over and above whitespace. */
	private static final String EMAIL_ILLEGAL_CHARS = "();:";

	private final ProfileFieldValidator profileFieldValidator;

	/** @throws ApiException 400 VALIDATION_FAILED carrying one entry per broken rule */
	public void validate(RegisterCommand command) {
		List<FieldViolation> violations = new ArrayList<>();

		validatePassword(command, violations);
		validateEmail(command.email(), violations);
		profileFieldValidator.validatePhoneNumber(command.phoneNumber(), violations);
		profileFieldValidator.validateFullName(command.fullName(), violations);
		validateAddress(command.address(), violations);

		if (!violations.isEmpty()) {
			throw ApiException.validationFailed(violations);
		}
	}

	private void validatePassword(RegisterCommand command, List<FieldViolation> violations) {
		String password = command.password();

		if (password == null || password.isEmpty()) {
			violations.add(FieldViolation.of("password", ErrorCode.PASSWORD_TOO_SHORT,
					"No password was provided."));
			return; // The remaining password rules would all restate this one.
		}

		if (password.length() < PASSWORD_MIN_LENGTH) {
			violations.add(FieldViolation.of("password", ErrorCode.PASSWORD_TOO_SHORT,
					"Yours has " + password.length() + "."));
		}
		if (password.chars().noneMatch(Character::isDigit)) {
			violations.add(FieldViolation.of("password", ErrorCode.PASSWORD_MISSING_DIGIT,
					"Yours has none."));
		}
		if (password.chars().allMatch(Character::isLetterOrDigit)) {
			violations.add(FieldViolation.of("password", ErrorCode.PASSWORD_MISSING_SPECIAL_CHARACTER,
					"The value you entered contains only letters and digits."));
		}
		if (password.chars().noneMatch(Character::isUpperCase)) {
			violations.add(FieldViolation.of("password", ErrorCode.PASSWORD_MISSING_UPPERCASE,
					"Yours is all lower case."));
		}
		if (!password.equals(command.confirmPassword())) {
			violations.add(FieldViolation.of("confirmPassword",
					ErrorCode.PASSWORD_CONFIRMATION_MISMATCH, "Confirm password differs."));
		}
	}

	private void validateEmail(String email, List<FieldViolation> violations) {
		if (email == null || email.isBlank()) {
			violations.add(FieldViolation.of("email", ErrorCode.EMAIL_AT_SYMBOL_INVALID,
					"No email was provided."));
			return;
		}

		String trimmed = email.trim();

		long atCount = trimmed.chars().filter(character -> character == '@').count();
		if (atCount != 1) {
			violations.add(FieldViolation.of("email", ErrorCode.EMAIL_AT_SYMBOL_INVALID,
					atCount == 0 ? "Yours has none." : "Yours has " + atCount + "."));
		} else if (!trimmed.substring(trimmed.indexOf('@') + 1).contains(".")) {
			// Only meaningful once we know there is exactly one @ to split on.
			violations.add(FieldViolation.of("email", ErrorCode.EMAIL_DOMAIN_INVALID,
					"The part after @ has no dot."));
		}

		if (trimmed.length() >= EMAIL_MAX_LENGTH) {
			violations.add(FieldViolation.of("email", ErrorCode.EMAIL_TOO_LONG,
					"Yours is " + trimmed.length() + " characters."));
		}

		boolean hasIllegalCharacter = trimmed.chars().anyMatch(character ->
				Character.isWhitespace(character) || EMAIL_ILLEGAL_CHARS.indexOf(character) >= 0);
		if (hasIllegalCharacter) {
			violations.add(FieldViolation.of("email", ErrorCode.EMAIL_ILLEGAL_CHARACTER,
					"Yours contains a space or one of ( ) ; : characters."));
		}
	}

	private void validateAddress(RegisterCommand.AddressCommand address,
			List<FieldViolation> violations) {

		if (address == null) {
			violations.add(FieldViolation.of("address", ErrorCode.ADDRESS_INCOMPLETE,
					"No address was provided."));
			return;
		}

		profileFieldValidator.validateAddress(address.streetNumber(), address.streetName(),
				address.ward(), address.provinceCode(), "address", violations);
	}
}
