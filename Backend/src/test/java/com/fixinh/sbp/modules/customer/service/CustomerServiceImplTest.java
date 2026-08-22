package com.fixinh.sbp.modules.customer.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.auth.interfaces.internal.CredentialRegionUpdater;
import com.fixinh.sbp.modules.customer.dto.internal.CompleteAddressCommand;
import com.fixinh.sbp.modules.customer.model.Address;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.repository.CustomerRepository;

class CustomerServiceImplTest {
	private CustomerRepository repository;
	private RegionResolver resolver;
	private CredentialRegionUpdater credentials;
	private ShardDirectoryRepository directory;
	private CustomerServiceImpl service;

	@BeforeEach
	void setUp() {
		repository = mock(CustomerRepository.class);
		resolver = mock(RegionResolver.class);
		credentials = mock(CredentialRegionUpdater.class);
		directory = mock(ShardDirectoryRepository.class);
		service = new CustomerServiceImpl(repository, resolver, credentials, directory);
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

	private Customer incompleteCustomer() {
		return Customer.builder().id("user-1").homeRegion(Region.SOUTH).build();
	}

	private CompleteAddressCommand command() {
		return new CompleteAddressCommand("1", "Main Street", "Ward 1", "HN");
	}
}
