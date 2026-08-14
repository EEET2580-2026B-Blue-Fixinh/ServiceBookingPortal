package com.fixinh.sbp.modules.auth.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RegisterCommand;
import com.fixinh.sbp.modules.auth.interfaces.external.AuthExternalService;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.AccountStatus;
import com.fixinh.sbp.modules.customer.model.Address;
import com.fixinh.sbp.modules.customer.model.Customer;

import lombok.RequiredArgsConstructor;

/** Registration and sign-in (API Documentation Sections 4.1 and 4.2). */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthExternalService {

	private static final String TOKEN_TYPE_BEARER = "Bearer";

	private final RegistrationValidator registrationValidator;
	private final CustomerInternalService customerService;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;

	@Override
	public RegisteredCustomerDto register(RegisterCommand command) {
		registrationValidator.validate(command);

		RegisterCommand.AddressCommand addressCommand = command.address();
		Address address = Address.builder()
				.streetNumber(addressCommand.streetNumber().trim())
				.streetName(addressCommand.streetName().trim())
				.ward(addressCommand.ward().trim())
				.provinceCode(addressCommand.provinceCode().trim())
				.build();

		// The customer module owns hashing, uniqueness and shard placement.
		Customer created = customerService.create(new CustomerCreateCommand(
				command.email(),
				command.phoneNumber(),
				command.password(),
				command.fullName(),
				address));

		return new RegisteredCustomerDto(
				created.getId(),
				created.getEmail(),
				created.getFullName(),
				created.getRole(),
				created.getAccountStatus(),
				created.getRegisteredAt());
	}

	@Override
	public AuthTokenDto login(LoginCommand command) {
		Optional<Customer> match = customerService.findByIdentifier(command.identifier());

		// Deliberately identical whether the account is unknown or the password is wrong, so the
		// endpoint cannot be used to discover which emails are registered.
		Customer customer = match
				.filter(candidate -> passwordEncoder.matches(command.password(),
						candidate.getPasswordHash()))
				.orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		// Checked only after the password, so a deactivated account is not revealed to someone
		// who cannot sign in anyway (Req 3.5.S2).
		if (customer.getAccountStatus() == AccountStatus.DEACTIVATED) {
			throw new ApiException(ErrorCode.AUTH_ACCOUNT_DEACTIVATED);
		}

		// The role travels from the stored account, not from an assumption about which store it
		// came from — so once an admin credential store exists, this issues admin tokens unchanged.
		return new AuthTokenDto(
				tokenProvider.generate(customer),
				TOKEN_TYPE_BEARER,
				tokenProvider.getAccessTokenTtlSeconds(),
				new AuthTokenDto.AuthUserDto(
						customer.getId(),
						customer.getFullName(),
						customer.getEmail(),
						customer.getRole()));
	}
}
