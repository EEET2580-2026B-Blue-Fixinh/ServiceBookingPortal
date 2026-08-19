package com.fixinh.sbp.modules.customer.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/**
 * The customer module's own logic. Only what registration and sign-in need is implemented here;
 * profile read/update (GET and PUT /customers/me) belong to a separate task.
 *
 * <p>Note what is no longer here: password hashing, and the email and phone uniqueness checks.
 * Both moved to the credential, which is one document in both stores — so uniqueness is enforced
 * by an index rather than by a check that two concurrent registrations could both pass.
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerInternalService, CustomerExternalService {

	private final CustomerRepository customerRepository;
	private final RegionResolver regionResolver;

	@Override
	public Customer createProfile(CustomerCreateCommand command) {
		Customer customer = Customer.builder()
				.id(command.id())
				.email(normaliseEmail(command.email()))
				.fullName(command.fullName().trim())
				.role(Role.CUSTOMER)
				.address(command.address())
				// Truncated to the precision BSON can hold, so the value returned by register is
				// the same value every later read reports.
				.registeredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
				.homeRegion(regionResolver.resolve(command.address().getProvinceCode()))
				.build();

		return customerRepository.save(customer);
	}

	@Override
	public Optional<Customer> findById(String id) {
		return customerRepository.findById(id);
	}

	/** What the booking module resolves a customer's home region through — see Req 3.1.U1. */
	@Override
	public Optional<Region> findHomeRegion(String customerId) {
		return customerRepository.findById(customerId).map(Customer::getHomeRegion);
	}

	/** Stored lower-cased so the denormalised copy matches the credential it was taken from. */
	private String normaliseEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
