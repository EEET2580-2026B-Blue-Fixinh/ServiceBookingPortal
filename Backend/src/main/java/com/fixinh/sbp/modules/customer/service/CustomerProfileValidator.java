package com.fixinh.sbp.modules.customer.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.exception.FieldViolation;
import com.fixinh.sbp.common.validation.ProfileFieldValidator;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateProfileCommand;

import lombok.RequiredArgsConstructor;

/** PUT /customers/me's field rules (API Documentation Section 6.2, Appendix C). */
@Component
@RequiredArgsConstructor
public class CustomerProfileValidator {

	private final ProfileFieldValidator profileFieldValidator;

	/** @throws ApiException 400 VALIDATION_FAILED carrying one entry per broken rule */
	public void validate(UpdateProfileCommand command) {
		List<FieldViolation> violations = new ArrayList<>();

		profileFieldValidator.validateFullName(command.fullName(), violations);
		profileFieldValidator.validatePhoneNumber(command.phoneNumber(), violations);

		UpdateProfileCommand.AddressCommand address = command.address();
		if (address == null) {
			violations.add(FieldViolation.of("address", ErrorCode.ADDRESS_INCOMPLETE,
					"No address was provided."));
		} else {
			profileFieldValidator.validateAddress(address.streetNumber(), address.streetName(),
					address.ward(), address.provinceCode(), "address", violations);
		}

		if (!violations.isEmpty()) {
			throw ApiException.validationFailed(violations);
		}
	}
}
