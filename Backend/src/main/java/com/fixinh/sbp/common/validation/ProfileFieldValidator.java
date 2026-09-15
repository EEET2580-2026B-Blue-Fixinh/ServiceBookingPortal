package com.fixinh.sbp.common.validation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.exception.FieldViolation;
import com.fixinh.sbp.common.sharding.RegionResolver;

import lombok.RequiredArgsConstructor;

/**
 * The full-name, phone-number and address rules (Appendix C) shared by registration (API
 * Documentation Section 4.1) and profile updates (Section 6.2) — the rules for a person's name
 * and contact details, not tied to either endpoint's request shape.
 */
@Component
@RequiredArgsConstructor
public class ProfileFieldValidator {

	private static final int NAME_MIN_LENGTH = 2;
	private static final int NAME_MAX_LENGTH = 100;
	private static final int PHONE_MIN_DIGITS = 7;
	private static final int PHONE_MAX_DIGITS = 11;

	private final RegionResolver regionResolver;

	public void validateFullName(String fullName, List<FieldViolation> violations) {
		String trimmed = fullName == null ? "" : fullName.trim();
		if (trimmed.length() < NAME_MIN_LENGTH || trimmed.length() > NAME_MAX_LENGTH) {
			violations.add(FieldViolation.of("fullName", ErrorCode.NAME_LENGTH_INVALID,
					"Yours is outside that range."));
		}
	}

	public void validatePhoneNumber(String phoneNumber, List<FieldViolation> violations) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			violations.add(FieldViolation.of("phoneNumber", ErrorCode.PHONE_LENGTH_INVALID,
					"No phone number was provided."));
			return;
		}

		String trimmed = phoneNumber.trim();

		if (!trimmed.chars().allMatch(Character::isDigit)) {
			violations.add(FieldViolation.of("phoneNumber", ErrorCode.PHONE_ILLEGAL_CHARACTER,
					"Yours contains a space or symbol."));
			return; // Length and prefix checks read as noise once the value is not digits.
		}
		if (trimmed.length() < PHONE_MIN_DIGITS || trimmed.length() > PHONE_MAX_DIGITS) {
			violations.add(FieldViolation.of("phoneNumber", ErrorCode.PHONE_LENGTH_INVALID,
					"Yours has " + trimmed.length() + "."));
		}
		if (!trimmed.startsWith("0")) {
			violations.add(FieldViolation.of("phoneNumber", ErrorCode.PHONE_PREFIX_INVALID,
					"Yours starts with " + trimmed.charAt(0) + "."));
		}
	}

	/**
	 * @param fieldPrefix what a violation's {@code field} is reported as, e.g. {@code "address"}
	 *                    so an incomplete address reports on {@code address} and an unknown
	 *                    province on {@code address.provinceCode}.
	 */
	public void validateAddress(String streetNumber, String streetName, String ward, String provinceCode,
			String fieldPrefix, List<FieldViolation> violations) {

		List<String> missing = new ArrayList<>();
		if (isBlank(streetNumber)) {
			missing.add("street number");
		}
		if (isBlank(streetName)) {
			missing.add("street name");
		}
		if (isBlank(ward)) {
			missing.add("ward");
		}
		if (isBlank(provinceCode)) {
			missing.add("city");
		}

		if (!missing.isEmpty()) {
			violations.add(FieldViolation.of(fieldPrefix, ErrorCode.ADDRESS_INCOMPLETE,
					String.join(", ", missing) + " is empty."));
			return; // An absent province code cannot also be an invalid one.
		}

		// The city must come from the list, so an unknown code never reaches shard resolution.
		if (!regionResolver.isKnown(provinceCode)) {
			violations.add(FieldViolation.of(fieldPrefix + ".provinceCode", ErrorCode.PROVINCE_CODE_INVALID,
					"The code sent is not a Vietnamese province or municipality."));
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
