package com.fixinh.sbp.modules.customer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.auth.interfaces.internal.AuthInternalService;
import com.fixinh.sbp.modules.auth.interfaces.internal.CredentialRegionUpdater;
import com.fixinh.sbp.modules.auth.model.AuthProvider;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.customer.dto.external.CustomerProfileDto;
import com.fixinh.sbp.modules.customer.dto.internal.CompleteAddressCommand;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateProfileCommand;
import com.fixinh.sbp.modules.customer.model.Address;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.repository.CustomerRepository;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

class CustomerServiceImplTest {
	private CustomerRepository repository;
	private RegionResolver resolver;
	private CredentialRegionUpdater credentials;
	private ShardDirectoryRepository directory;
	private AuthInternalService authService;
	private ReferenceExternalService referenceService;
	private CustomerProfileValidator profileValidator;
	private CustomerServiceImpl service;

	@BeforeEach
	void setUp() {
		repository = mock(CustomerRepository.class);
		resolver = mock(RegionResolver.class);
		credentials = mock(CredentialRegionUpdater.class);
		directory = mock(ShardDirectoryRepository.class);
		authService = mock(AuthInternalService.class);
		referenceService = mock(ReferenceExternalService.class);
		profileValidator = mock(CustomerProfileValidator.class);
		service = new CustomerServiceImpl(
				repository, resolver, credentials, directory, authService, referenceService, profileValidator);
	}

	@Test
	void completesAddressAndMovesCredentialAndDirectory() {
		Customer customer = incompleteCustomer();
		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		when(resolver.isKnown("HN")).thenReturn(true);
		when(resolver.resolve("HN")).thenReturn(Region.NORTH);

		service.completeAddress("user-1", command());

		verify(repository).relocate(customer, Region.SOUTH);
		verify(credentials).relocateGoogleCredential("user-1", Region.NORTH);
		verify(directory).register(SubjectType.CUSTOMER, "user-1", Region.NORTH);
	}

	@Test
	void restoresCustomerAndCredentialWhenDirectoryFails() {
		Customer customer = incompleteCustomer();
		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		when(resolver.isKnown("HN")).thenReturn(true);
		when(resolver.resolve("HN")).thenReturn(Region.NORTH);
		doThrow(new IllegalStateException("directory unavailable"))
				.when(directory).register(SubjectType.CUSTOMER, "user-1", Region.NORTH);

		assertThrows(IllegalStateException.class,
				() -> service.completeAddress("user-1", command()));
		verify(credentials).relocateGoogleCredential("user-1", Region.SOUTH);
		verify(repository).relocate(customer, Region.NORTH);
	}

	@Test
	void rejectsAProfileThatAlreadyHasAnAddress() {
		Customer customer = incompleteCustomer();
		customer.setAddress(Address.builder().provinceCode("HN").build());
		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		assertThrows(ApiException.class, () -> service.completeAddress("user-1", command()));
	}

	@Test
	void buildsProfileWithAuthProvidersInDeclarationOrderAndProvinceName() {
		Address address = Address.builder().streetNumber("1").streetName("Main Street")
				.ward("Ward 1").provinceCode("HCM").build();
		Customer customer = Customer.builder().id("user-1").fullName("Nguyen Hong Anh")
				.email("an@example.com").address(address).homeRegion(Region.SOUTH).build();
		UserCredential credential = UserCredential.builder().id("user-1").phoneNumber("0912345678")
				.accountStatus(AccountStatus.ACTIVE)
				.authProviders(Set.of(AuthProvider.GOOGLE, AuthProvider.PASSWORD))
				.build();
		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		when(authService.findById("user-1")).thenReturn(Optional.of(credential));
		when(referenceService.listProvinces()).thenReturn(
				List.of(new ProvinceDto("HCM", "Ho Chi Minh City", true, "79")));

		CustomerProfileDto profile = service.getMyProfile("user-1");

		assertEquals(List.of("PASSWORD", "GOOGLE"), profile.authProviders());
		assertEquals("Ho Chi Minh City", profile.address().provinceName());
		assertEquals(AccountStatus.ACTIVE, profile.accountStatus());
		assertEquals("0912345678", profile.phoneNumber());
	}

	@Test
	void updatesProfileWithoutRelocatingWhenProvinceUnchanged() {
		Address address = Address.builder().streetNumber("1").streetName("Main Street")
				.ward("Ward 1").provinceCode("HCM").build();
		Customer customer = Customer.builder().id("user-1").fullName("Old Name")
				.email("an@example.com").address(address).homeRegion(Region.SOUTH).build();
		UserCredential credential = UserCredential.builder().id("user-1").phoneNumber("0912345678")
				.accountStatus(AccountStatus.ACTIVE).authProviders(Set.of(AuthProvider.PASSWORD)).build();

		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		when(authService.updatePhoneNumber("user-1", "0912345678")).thenReturn(credential);
		when(resolver.resolve("HCM")).thenReturn(Region.SOUTH);
		when(referenceService.listProvinces()).thenReturn(
				List.of(new ProvinceDto("HCM", "Ho Chi Minh City", true, "79")));

		CustomerProfileDto updated =
				service.updateMyProfile("user-1", updateCommand("New Name", "0912345678", "HCM"));

		assertEquals("New Name", updated.fullName());
		verify(repository).relocate(customer, Region.SOUTH);
		verify(credentials, never()).relocateGoogleCredential(any(), any());
		verify(directory, never()).register(any(), any(), any());
	}

	@Test
	void relocatesCredentialAndDirectoryWhenProvinceChanges() {
		Address address = Address.builder().streetNumber("1").streetName("Main Street")
				.ward("Ward 1").provinceCode("HCM").build();
		Customer customer = Customer.builder().id("user-1").fullName("Name")
				.email("an@example.com").address(address).homeRegion(Region.SOUTH).build();
		UserCredential credential = UserCredential.builder().id("user-1").phoneNumber("0912345678")
				.accountStatus(AccountStatus.ACTIVE).authProviders(Set.of(AuthProvider.PASSWORD)).build();

		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		when(authService.updatePhoneNumber("user-1", "0912345678")).thenReturn(credential);
		when(resolver.resolve("HN")).thenReturn(Region.NORTH);
		when(referenceService.listProvinces()).thenReturn(List.of());

		service.updateMyProfile("user-1", updateCommand("Name", "0912345678", "HN"));

		verify(repository).relocate(customer, Region.SOUTH);
		verify(credentials).relocateGoogleCredential("user-1", Region.NORTH);
		verify(directory).register(SubjectType.CUSTOMER, "user-1", Region.NORTH);
	}

	@Test
	void propagatesPhoneConflictWithoutTouchingTheProfile() {
		Customer customer = incompleteCustomer();
		when(repository.findById("user-1")).thenReturn(Optional.of(customer));
		when(authService.updatePhoneNumber("user-1", "0912345678"))
				.thenThrow(new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED));

		assertThrows(ApiException.class,
				() -> service.updateMyProfile("user-1", updateCommand("Name", "0912345678", "HN")));
		verify(repository, never()).relocate(any(), any());
	}

	private UpdateProfileCommand updateCommand(String fullName, String phoneNumber, String provinceCode) {
		return new UpdateProfileCommand(fullName, phoneNumber,
				new UpdateProfileCommand.AddressCommand("1", "Main Street", "Ward 1", provinceCode));
	}

	private Customer incompleteCustomer() {
		return Customer.builder().id("user-1").homeRegion(Region.SOUTH).build();
	}

	private CompleteAddressCommand command() {
		return new CompleteAddressCommand("1", "Main Street", "Ward 1", "HN");
	}
}
