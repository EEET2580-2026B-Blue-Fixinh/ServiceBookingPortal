package com.fixinh.sbp.modules.customer.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.AccountStatus;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.model.Role;
import com.fixinh.sbp.modules.customer.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/**
 * The customer module's own logic. Only the parts registration and sign-in need are implemented
 * here; profile read/update (GET and PUT /customers/me) belong to a separate task.
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerInternalService, CustomerExternalService {

	/** Anything that looks like a phone number rather than an email: digits, optionally spaced. */
	private static final String DIGITS_ONLY = "\\d+";

	private final CustomerRepository customerRepository;
	private final PasswordEncoder passwordEncoder;
	private final RegionResolver regionResolver;

	@Override
	public Customer create(CustomerCreateCommand command) {
		String email = normaliseEmail(command.email());
		String phoneNumber = command.phoneNumber().trim();

		// Uniqueness is system-wide, so these check both shards (Req 3.1.S2).
		if (customerRepository.existsByEmail(email)) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
		if (customerRepository.existsByPhoneNumber(phoneNumber)) {
			throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
		}

		Customer customer = Customer.builder()
				.email(email)
				.phoneNumber(phoneNumber)
				.passwordHash(passwordEncoder.encode(command.rawPassword()))
				.fullName(command.fullName().trim())
				.role(Role.CUSTOMER)
				.accountStatus(AccountStatus.ACTIVE)
				.address(command.address())
				.authProviders(List.of("PASSWORD"))
				// Truncated to the precision BSON can hold, so the value returned by register is
				// the same value every later read reports.
				.registeredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
				.homeRegion(regionResolver.resolve(command.address().getProvinceCode()))
				.build();

		return customerRepository.save(customer);
	}

	@Override
	public Optional<Customer> findByIdentifier(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			return Optional.empty();
		}

		String trimmed = identifier.trim();

		// One field carries both, so the client does not have to say which it typed. Digits-only
		// means phone; anything else is treated as an email.
		return trimmed.matches(DIGITS_ONLY)
				? customerRepository.findByPhoneNumber(trimmed)
				: customerRepository.findByEmail(trimmed);
	}

	@Override
	public boolean emailExists(String email) {
		return customerRepository.existsByEmail(normaliseEmail(email));
	}

	@Override
	public boolean phoneExists(String phoneNumber) {
		return customerRepository.existsByPhoneNumber(phoneNumber);
	}

	@Override
	public Optional<Region> findHomeRegion(String customerId) {
		return customerRepository.findById(customerId).map(Customer::getHomeRegion);
	}

	private String normaliseEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
}
