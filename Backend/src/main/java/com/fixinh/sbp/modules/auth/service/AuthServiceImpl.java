package com.fixinh.sbp.modules.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RegisterCommand;
import com.fixinh.sbp.modules.auth.interfaces.external.AuthExternalService;
import com.fixinh.sbp.modules.auth.interfaces.internal.AuthInternalService;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.auth.repository.UserCredentialRepository;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.Address;
import com.fixinh.sbp.modules.customer.model.Customer;

import lombok.RequiredArgsConstructor;

/** Registration and sign-in (API Documentation Sections 4.1 and 4.2). */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthExternalService, AuthInternalService {

	private static final String TOKEN_TYPE_BEARER = "Bearer";
	private static final String PROVIDER_PASSWORD = "PASSWORD";

	/** Anything that looks like a phone number rather than an email address. */
	private static final String DIGITS_ONLY = "\\d+";

	private final RegistrationValidator registrationValidator;
	private final UserCredentialRepository credentialRepository;
	private final CustomerInternalService customerService;
	private final RegionResolver regionResolver;
	private final AccountProfileReader profileReader;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;

	@Override
	public RegisteredCustomerDto register(RegisterCommand command) {
		registrationValidator.validate(command);

		String email = command.email().trim().toLowerCase(Locale.ROOT);
		String phoneNumber = command.phoneNumber().trim();

		// These two checks are what enforces uniqueness across regions: each store's unique index
		// sees only its own half, so an email taken in the north is invisible to the south index.
		// A check-then-insert, so two simultaneous registrations routing to different stores
		// could in principle both pass — accepted, and noted in UserCredentialRepository.
		if (credentialRepository.existsByEmail(email)) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
		if (credentialRepository.existsByPhoneNumber(phoneNumber)) {
			throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
		}

		// Generated here rather than by Mongo, because the profile written afterwards must carry
		// the same id and cannot wait for one to come back.
		String userId = ObjectId.get().toHexString();

		RegisterCommand.AddressCommand addressCommand = command.address();
		Address address = Address.builder()
				.streetNumber(addressCommand.streetNumber().trim())
				.streetName(addressCommand.streetName().trim())
				.ward(addressCommand.ward().trim())
				.provinceCode(addressCommand.provinceCode().trim())
				.build();

		// The credential is filed in the same store as the profile it identifies, so an account
		// is never split across regions. The validator has already rejected an unknown code, so
		// this cannot throw.
		Region homeRegion = regionResolver.resolve(address.getProvinceCode());

		UserCredential credential = UserCredential.builder()
				.id(userId)
				.email(email)
				.phoneNumber(phoneNumber)
				.passwordHash(passwordEncoder.encode(command.password()))
				.accountStatus(AccountStatus.ACTIVE)
				.accountRole(Role.CUSTOMER)
				.authProviders(List.of(PROVIDER_PASSWORD))
				.registeredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
				.homeRegion(homeRegion)
				.build();

		// Credential first, so a duplicate that its store's index catches fails before a profile
		// exists to be left orphaned.
		try {
			credentialRepository.save(credential);
		} catch (DuplicateKeyException ex) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}

		// The customer module owns shard placement.
		Customer profile = customerService.createProfile(
				new CustomerCreateCommand(userId, email, command.fullName(), address));

		return new RegisteredCustomerDto(
				profile.getId(),
				profile.getEmail(),
				profile.getFullName(),
				credential.getAccountRole(),
				credential.getAccountStatus(),
				credential.getRegisteredAt());
	}

	@Override
	public AuthTokenDto login(LoginCommand command) {
		// An identifier carries no province, so the owning store is unknown until the account is
		// found: south is asked first, then north. A southern account costs one query, a northern
		// one two — the price of each credential living in exactly one region.
		Optional<UserCredential> match = findCredential(command.identifier());

		// Deliberately identical whether the account is unknown or the password is wrong, so the
		// endpoint cannot be used to discover which emails are registered.
		UserCredential credential = match
				.filter(candidate -> matches(command.password(), candidate.getPasswordHash()))
				.orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		// Checked only after the password, so a deactivated account is not revealed to someone
		// who cannot sign in anyway (Req 3.5.S2).
		if (credential.getAccountStatus() == AccountStatus.DEACTIVATED) {
			throw new ApiException(ErrorCode.AUTH_ACCOUNT_DEACTIVATED);
		}

		// The role in the token comes from the credential's discriminator, never from which
		// collection the profile happened to be found in.
		return new AuthTokenDto(
				tokenProvider.generate(credential),
				TOKEN_TYPE_BEARER,
				tokenProvider.getAccessTokenTtlSeconds(),
				new AuthTokenDto.AuthUserDto(
						credential.getId(),
						profileReader.fullNameOf(credential),
						credential.getEmail(),
						credential.getAccountRole()));
	}

	@Override
	public List<UserCredential> listByRole(Role role) {
		return credentialRepository.findByAccountRole(role);
	}

	@Override
	public UserCredential setAccountStatus(String id, AccountStatus status) {
		UserCredential credential = credentialRepository.findById(id)
				.orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));

		credential.setAccountStatus(status);

		return credentialRepository.save(credential);
	}

	/** One field takes both, so the server works out which was typed (see LoginCommand). */
	private Optional<UserCredential> findCredential(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			return Optional.empty();
		}

		String trimmed = identifier.trim();
		return trimmed.matches(DIGITS_ONLY)
				? credentialRepository.findByPhoneNumber(trimmed)
				: credentialRepository.findByEmail(trimmed);
	}

	/**
	 * A federated-only account has a null hash and must not be signable into with a password.
	 * The encoder returns false for null anyway, but only after logging a warning that reads
	 * like a fault rather than the expected outcome.
	 */
	private boolean matches(String rawPassword, String storedHash) {
		return storedHash != null && passwordEncoder.matches(rawPassword, storedHash);
	}
}
