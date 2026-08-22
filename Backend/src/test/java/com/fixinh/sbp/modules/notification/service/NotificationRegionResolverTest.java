package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;

/**
 * The directory is mocked; {@link RegionResolver} is not.
 *
 * <p>Mocking the province table would only prove the test agrees with itself. The real one is
 * a pure lookup over an enum with no collaborators, so using it means these assertions also
 * check that {@code HCM} and {@code HN} really do land in the stores the region boundary says
 * they should.
 */
class NotificationRegionResolverTest {

	private static final String STAFF_ID = "STF-001";
	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";

	private ShardDirectoryRepository shardDirectory;
	private CustomerExternalService customerService;
	private NotificationRegionResolver resolver;

	@BeforeEach
	void setUp() {
		shardDirectory = mock(ShardDirectoryRepository.class);
		customerService = mock(CustomerExternalService.class);
		resolver = new NotificationRegionResolver(
				shardDirectory, new RegionResolver(), customerService);
	}

	@Test
	void usesTheDirectoryWhenTheSubjectIsAlreadyKnown() {
		when(shardDirectory.findRegion(SubjectType.STAFF, STAFF_ID))
				.thenReturn(Optional.of(Region.NORTH));

		Region region = resolver.regionFor(RecipientType.STAFF, STAFF_ID, "HCM");

		assertEquals(Region.NORTH, region,
				"a known subject stays where it was placed, whatever province is passed now");
		verify(shardDirectory, never()).register(any(), anyString(), any());
	}

	@Test
	void resolvesFromTheBranchProvinceAndRemembersIt() {
		when(shardDirectory.findRegion(SubjectType.STAFF, STAFF_ID)).thenReturn(Optional.empty());
		when(shardDirectory.register(SubjectType.STAFF, STAFF_ID, Region.SOUTH))
				.thenReturn(Region.SOUTH);

		Region region = resolver.regionFor(RecipientType.STAFF, STAFF_ID, "HCM");

		assertEquals(Region.SOUTH, region, "Ho Chi Minh City is in the southern store");
		// The registration is the point: the next notification to this staff member needs no
		// province, which is what makes the directory worth writing to at all.
		verify(shardDirectory).register(SubjectType.STAFF, STAFF_ID, Region.SOUTH);
	}

	@Test
	void routesANorthernBranchToTheNorthernStore() {
		when(shardDirectory.findRegion(SubjectType.STAFF, STAFF_ID)).thenReturn(Optional.empty());
		when(shardDirectory.register(SubjectType.STAFF, STAFF_ID, Region.NORTH))
				.thenReturn(Region.NORTH);

		assertEquals(Region.NORTH, resolver.regionFor(RecipientType.STAFF, STAFF_ID, "HN"));
	}

	@Test
	void mapsACustomerRecipientToACustomerSubject() {
		when(shardDirectory.findRegion(SubjectType.CUSTOMER, "CUS-9"))
				.thenReturn(Optional.of(Region.SOUTH));

		resolver.regionFor(RecipientType.CUSTOMER, "CUS-9", null);

		// A customer id and a staff id could collide; the subject type is what keeps them apart.
		verify(shardDirectory).findRegion(SubjectType.CUSTOMER, "CUS-9");
	}

	@Test
	void rejectsAnUnknownProvinceRatherThanPickingAStore() {
		when(shardDirectory.findRegion(SubjectType.STAFF, STAFF_ID)).thenReturn(Optional.empty());

		ApiException thrown = assertThrows(ApiException.class,
				() -> resolver.regionFor(RecipientType.STAFF, STAFF_ID, "ATLANTIS"));

		assertEquals(ErrorCode.PROVINCE_CODE_INVALID, thrown.getErrorCode());
		verify(shardDirectory, never()).register(any(), anyString(), any());
	}

	@Test
	void rejectsAMissingProvinceForAnUnknownSubject() {
		when(shardDirectory.findRegion(SubjectType.STAFF, STAFF_ID)).thenReturn(Optional.empty());

		ApiException thrown = assertThrows(ApiException.class,
				() -> resolver.regionFor(RecipientType.STAFF, STAFF_ID, "  "));

		assertEquals(ErrorCode.PROVINCE_CODE_INVALID, thrown.getErrorCode());
		verify(shardDirectory, never()).register(any(), anyString(), any());
	}

	@Test
	void rejectsAMissingRecipientBeforeTouchingTheDirectory() {
		assertThrows(IllegalArgumentException.class,
				() -> resolver.regionFor(RecipientType.STAFF, "  ", "HCM"));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.regionFor(null, STAFF_ID, "HCM"));
	}


	// --- regionForCustomer: the read path (§9.1, §9.2) -------------------------------------

	@Test
	void readsACustomersStoreStraightFromTheDirectory() {
		when(shardDirectory.findRegion(SubjectType.CUSTOMER, CUSTOMER_ID))
				.thenReturn(Optional.of(Region.SOUTH));

		assertEquals(Region.SOUTH, resolver.regionForCustomer(CUSTOMER_ID));

		verifyNoInteractions(customerService);
		verify(shardDirectory, never()).register(any(), anyString(), any());
	}

	@Test
	void fallsBackToTheProfileAndBackfillsTheDirectory() {
		when(shardDirectory.findRegion(SubjectType.CUSTOMER, CUSTOMER_ID))
				.thenReturn(Optional.empty());
		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.of(Region.NORTH));
		when(shardDirectory.register(SubjectType.CUSTOMER, CUSTOMER_ID, Region.NORTH))
				.thenReturn(Region.NORTH);

		assertEquals(Region.NORTH, resolver.regionForCustomer(CUSTOMER_ID));

		// The backfill is the point: the next read must not need the profile again.
		verify(shardDirectory).register(SubjectType.CUSTOMER, CUSTOMER_ID, Region.NORTH);
	}

	@Test
	void stillAnswersWhenTheDirectoryCannotBeWrittenTo() {
		when(shardDirectory.findRegion(SubjectType.CUSTOMER, CUSTOMER_ID))
				.thenReturn(Optional.empty());
		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.of(Region.SOUTH));
		when(shardDirectory.register(any(), anyString(), any()))
				.thenThrow(new IllegalStateException("both stores refused the write"));

		assertEquals(Region.SOUTH, resolver.regionForCustomer(CUSTOMER_ID),
				"a failed cache write must not discard an answer the profile already gave");
	}

	@Test
	void treatsAnAuthenticatedCallerWithNoProfileAsAFaultNotANotFound() {
		when(shardDirectory.findRegion(SubjectType.CUSTOMER, CUSTOMER_ID))
				.thenReturn(Optional.empty());
		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.empty());

		// Not ApiException: the caller passed authentication, so a missing profile is our
		// data being wrong, not their request. Matches BookingServiceImpl on the same case.
		assertThrows(IllegalStateException.class, () -> resolver.regionForCustomer(CUSTOMER_ID));
	}

	@Test
	void rejectsAMissingCustomerIdBeforeTouchingTheDirectory() {
		assertThrows(IllegalArgumentException.class, () -> resolver.regionForCustomer("  "));
		assertThrows(IllegalArgumentException.class, () -> resolver.regionForCustomer(null));

		verifyNoInteractions(shardDirectory, customerService);
	}
}