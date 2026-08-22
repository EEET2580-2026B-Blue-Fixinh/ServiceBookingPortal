package com.fixinh.sbp.modules.customer.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateAddressCommand;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.Address;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/**
 * The customer module's own logic. Only what registration, sign-in, and the admin customer
 * screen need is implemented here; profile read/update (GET and PUT /customers/me) belong to a
 * separate task.
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
				// Google OAuth users start in south region until they complete profile (Req 3.2.U3)
				.homeRegion(command.address() != null 
						? regionResolver.resolve(command.address().getProvinceCode()) 
						: Region.SOUTH)
				.build();

		return customerRepository.save(customer);
	}

	@Override
	public Optional<Customer> findById(String id) {
		return customerRepository.findById(id);
	}

	@Override
	public Region updateAddress(UpdateAddressCommand command) {
		// Find the customer profile
		Customer customer = customerRepository.findById(command.customerId())
				.orElseThrow(() -> new IllegalArgumentException("Customer not found: " + command.customerId()));

		// Resolve the home region from the new address
		Region previousRegion = customer.getHomeRegion();
		Region homeRegion = regionResolver.resolve(command.address().getProvinceCode());

		// Update the customer profile with the new address and region
		customer.setAddress(command.address());
		customer.setHomeRegion(homeRegion);
		customerRepository.relocate(customer, previousRegion);

		// Return the region so the caller can update the credential
		return homeRegion;
	}

	@Override
	public void restoreLocation(String customerId, Address address, Region region) {
		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
		Region currentRegion = customer.getHomeRegion();
		customer.setAddress(address);
		customer.setHomeRegion(region);
		customerRepository.relocate(customer, currentRegion);
	}

	/** What the booking module resolves a customer's home region through — see Req 3.1.U1. */
	@Override
	public Optional<Region> findHomeRegion(String customerId) {
		return customerRepository.findById(customerId).map(Customer::getHomeRegion);
	}

	@Override
	public List<Customer> listAll() {
		return customerRepository.findAll();
	}

	/** Stored lower-cased so the denormalised copy matches the credential it was taken from. */
	private String normaliseEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
