package com.fixinh.sbp.modules.booking.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.exception.FieldViolation;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;

import lombok.RequiredArgsConstructor;

/**
 * Field-presence and -format rules for a booking request (API Documentation §7.1), checked
 * before any of it is treated as meaningful. Semantic rules that need more than the request
 * itself — whether the date has passed, whether the service exists — are 404/422s the service
 * layer raises afterwards, not field violations here (§7.1's own error table treats them
 * separately from 400 VALIDATION_FAILED).
 *
 * <p>Every rule is checked and every failure is returned together, same contract as
 * {@code RegistrationValidator}: one round trip fixes everything wrong with the request, not one
 * field at a time. No Appendix C codes exist for booking fields, so violations here carry the
 * generic {@code VALIDATION_FAILED} code, mirroring how {@code GlobalExceptionHandler}'s own
 * bean-validation fallback already reports a missing field.
 */
@Component
@RequiredArgsConstructor
public class BookingCreateValidator {

	private static final int ISSUE_DESCRIPTION_MIN_LENGTH = 10;
	private static final int ISSUE_DESCRIPTION_MAX_LENGTH = 1000;

	private final RegionResolver regionResolver;

	/** @throws ApiException 400 VALIDATION_FAILED carrying one entry per broken rule */
	public void validate(BookingCreateCommand command) {
		List<FieldViolation> violations = new ArrayList<>();

		if (isBlank(command.serviceId())) {
			violations.add(missing("serviceId", "No repair service was selected."));
		}
		validateIssueDescription(command.issueDescription(), violations);
		if (command.preferredServiceDate() == null) {
			violations.add(missing("preferredServiceDate", "No preferred date was provided."));
		}
		if (command.availableStartTime() == null) {
			violations.add(missing("availableStartTime", "No start time was provided."));
		}
		if (command.availableEndTime() == null) {
			violations.add(missing("availableEndTime", "No end time was provided."));
		}
		validateAddress(command.serviceAddress(), violations);

		if (!violations.isEmpty()) {
			throw ApiException.validationFailed(violations);
		}
	}

	private void validateIssueDescription(String issueDescription, List<FieldViolation> violations) {
		String trimmed = issueDescription == null ? "" : issueDescription.trim();
		if (trimmed.length() < ISSUE_DESCRIPTION_MIN_LENGTH || trimmed.length() > ISSUE_DESCRIPTION_MAX_LENGTH) {
			violations.add(new FieldViolation("issueDescription", ErrorCode.VALIDATION_FAILED.name(),
					"Issue description must be between " + ISSUE_DESCRIPTION_MIN_LENGTH + " and "
							+ ISSUE_DESCRIPTION_MAX_LENGTH + " characters.",
					"Yours is " + trimmed.length() + ".", null));
		}
	}

	private void validateAddress(BookingCreateCommand.AddressCommand address,
			List<FieldViolation> violations) {

		if (address == null) {
			violations.add(FieldViolation.of("serviceAddress", ErrorCode.ADDRESS_INCOMPLETE,
					"No service address was provided."));
			return;
		}

		List<String> missing = new ArrayList<>();
		if (isBlank(address.streetNumber())) {
			missing.add("street number");
		}
		if (isBlank(address.streetName())) {
			missing.add("street name");
		}
		if (isBlank(address.ward())) {
			missing.add("ward");
		}
		if (isBlank(address.provinceCode())) {
			missing.add("city");
		}

		if (!missing.isEmpty()) {
			violations.add(FieldViolation.of("serviceAddress", ErrorCode.ADDRESS_INCOMPLETE,
					String.join(", ", missing) + " is empty."));
			return; // An absent province code cannot also be an invalid one.
		}

		if (!regionResolver.isKnown(address.provinceCode())) {
			violations.add(FieldViolation.of("serviceAddress.provinceCode",
					ErrorCode.PROVINCE_CODE_INVALID,
					"The code sent is not a Vietnamese province or municipality."));
		}
	}

	private FieldViolation missing(String field, String cause) {
		return new FieldViolation(field, ErrorCode.VALIDATION_FAILED.name(),
				ErrorCode.VALIDATION_FAILED.getMessage(), cause, null);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
