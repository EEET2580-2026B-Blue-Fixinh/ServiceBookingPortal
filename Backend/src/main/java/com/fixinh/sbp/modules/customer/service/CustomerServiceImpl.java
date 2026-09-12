package com.fixinh.sbp.modules.customer.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.auth.interfaces.internal.AuthInternalService;
import com.fixinh.sbp.modules.auth.interfaces.internal.CredentialRegionUpdater;
import com.fixinh.sbp.modules.auth.model.AuthProvider;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.customer.dto.external.CustomerProfileDto;
import com.fixinh.sbp.modules.customer.dto.internal.CompleteAddressCommand;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.Address;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.repository.CustomerRepository;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

/**
 * The customer module's own logic. Only what registration, sign-in, the admin customer screen,
 * and the account page need is implemented here.
 *
 * <p>Note what is no longer here: password hashing, and the email and phone uniqueness checks.
 * Both moved to the credential, which is one document in both stores — so uniqueness is enforced
 * by an index rather than by a check that two concurrent registrations could both pass.
 */
@Service
public class CustomerServiceImpl implements CustomerInternalService, CustomerExternalService {

	private final CustomerRepository customerRepository;
	private final RegionResolver regionResolver;
	private final CredentialRegionUpdater credentialRegionUpdater;
	private final ShardDirectoryRepository shardDirectory;

	/**
	 * Lazy because the dependency runs the other way at sign-in and registration: auth resolves
	 * a customer's display name and creates their profile through this module, so an eager
	 * reference here would be circular. Only {@link #getMyProfile} uses it, well after sign-in,
	 * so paying for the proxy once is cheap — the same trade-off {@code AdminServiceImpl} makes
	 * for its own reference to this service.
	 */
	private final AuthInternalService authService;

	/** The province catalogue, so {@link #getMyProfile} can denormalise a name onto the code. */
	private final ReferenceExternalService referenceService;

	/**
	 * Written out by hand rather than via {@code @RequiredArgsConstructor}: Lombok does not copy
	 * a field's {@code @Lazy} onto the generated constructor parameter, so the annotation would
	 * silently do nothing and the circular-bean error would come back.
	 */
	public CustomerServiceImpl(
			CustomerRepository customerRepository,
			RegionResolver regionResolver,
			CredentialRegionUpdater credentialRegionUpdater,
			ShardDirectoryRepository shardDirectory,
			@Lazy AuthInternalService authService,
			ReferenceExternalService referenceService) {
		this.customerRepository = customerRepository;
		this.regionResolver = regionResolver;
		this.credentialRegionUpdater = credentialRegionUpdater;
		this.shardDirectory = shardDirectory;
		this.authService = authService;
		this.referenceService = referenceService;
	}

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
				// Address-less users stay in the default region until shard placement is known.
				.homeRegion(command.address() != null 
						? regionResolver.resolve(command.address().getProvinceCode()) 
						: regionResolver.defaultRegion())
				.build();

		return customerRepository.save(customer);
	}

	@Override
	public Optional<Customer> findById(String id) {
		return customerRepository.findById(id);
	}

	private void restoreLocation(String customerId, Address address, Region region) {
		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
		Region currentRegion = customer.getHomeRegion();
		customer.setAddress(address);
		customer.setHomeRegion(region);
		customerRepository.relocate(customer, currentRegion);
	}

	@Override
	public void completeAddress(String customerId, CompleteAddressCommand command) {
		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));
		if (customer.getAddress() != null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED);
		}
		String provinceCode = command.provinceCode().trim();
		if (!regionResolver.isKnown(provinceCode)) {
			throw new ApiException(ErrorCode.PROVINCE_CODE_INVALID);
		}
		Region previousRegion = customer.getHomeRegion();
		Address previousAddress = customer.getAddress();
		Address address = Address.builder()
				.streetNumber(command.streetNumber().trim())
				.streetName(command.streetName().trim())
				.ward(command.ward().trim())
				.provinceCode(provinceCode)
				.build();
		Region targetRegion = regionResolver.resolve(address.getProvinceCode());
		customer.setAddress(address);
		customer.setHomeRegion(targetRegion);
		customerRepository.relocate(customer, previousRegion);
		try {
			credentialRegionUpdater.relocateGoogleCredential(customerId, targetRegion);
			shardDirectory.register(SubjectType.CUSTOMER, customerId, targetRegion);
		}
		catch (RuntimeException ex) {
			try {
				credentialRegionUpdater.relocateGoogleCredential(customerId, previousRegion);
			}
			finally {
				restoreLocation(customerId, previousAddress, previousRegion);
			}
			throw ex;
		}
	}

	/** What the booking module resolves a customer's home region through — see Req 3.1.U1. */
	@Override
	public Optional<Region> findHomeRegion(String customerId) {
		return customerRepository.findById(customerId).map(Customer::getHomeRegion);
	}

	@Override
	public CustomerProfileDto getMyProfile(String customerId) {
		Customer profile = customerRepository.findById(customerId)
				.orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));
		UserCredential credential = authService.findById(customerId)
				.orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));

		Set<AuthProvider> providers = credential.getAuthProviders();
		// Declaration order (PASSWORD, GOOGLE), not the HashSet's, so the response is stable
		// across calls rather than depending on hash bucket order (API Documentation Section 6.1
		// shows PASSWORD before GOOGLE).
		List<String> authProviders = providers == null
				? List.of()
				: Arrays.stream(AuthProvider.values())
						.filter(providers::contains)
						.map(Enum::name)
						.toList();

		String provinceName = null;
		Address address = profile.getAddress();
		if (address != null) {
			provinceName = referenceService.listProvinces().stream()
					.filter(province -> province.provinceCode().equals(address.getProvinceCode()))
					.map(ProvinceDto::provinceName)
					.findFirst()
					.orElse(null);
		}

		return CustomerProfileDto.from(
				profile, credential.getPhoneNumber(), credential.getAccountStatus(), authProviders, provinceName);
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
