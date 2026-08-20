package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	private ShardDirectoryRepository shardDirectory;
	private NotificationRegionResolver resolver;

	@BeforeEach
	void setUp() {
		shardDirectory = mock(ShardDirectoryRepository.class);
		resolver = new NotificationRegionResolver(shardDirectory, new RegionResolver());
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
}