package com.fixinh.sbp.common.smp.client.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.dto.EmploymentStatus;
import com.fixinh.sbp.common.smp.dto.SmpAddress;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;
import com.fixinh.sbp.common.smp.dto.StaffRole;

import tools.jackson.databind.json.JsonMapper;

class MockSmpStaffClientTest {

	private MockSmpStaffClient client;

	@BeforeEach
	void setUp() {
		client = new MockSmpStaffClient(new JsonMapper());
	}

	@Test
	void loadsTheBundledFixtureOnConstruction() {
		List<SmpStaffDto> all = client.listStaff(null);
		assertFalse(all.isEmpty());
		assertTrue(all.stream().anyMatch(staff -> staff.staffId().equals("TEC-014")));
	}

	@Test
	void searchMatchesByNameStaffIdOrEmailCaseInsensitively() {
		assertTrue(client.listStaff("john tran").stream().anyMatch(s -> s.staffId().equals("TEC-014")));
		assertTrue(client.listStaff("tec-014").stream().anyMatch(s -> s.staffId().equals("TEC-014")));
		assertTrue(client.listStaff("JOHN.TRAN@FIXINH.VN").stream().anyMatch(s -> s.staffId().equals("TEC-014")));
		assertTrue(client.listStaff("no such person").isEmpty());
	}

	@Test
	void findStaffReturnsEmptyForAnUnknownId() {
		assertTrue(client.findStaff("TEC-999").isEmpty());
	}

	@Test
	void findStaffReturnsTheMatchingRecord() {
		Optional<SmpStaffDto> found = client.findStaff("TEC-014");
		assertTrue(found.isPresent());
		assertEquals("John Tran", found.get().fullName());
	}

	@Test
	void createStaffAssignsAFreshIdAndAnInitialPassword() {
		SmpStaffDto created = client.createStaff(createRequest("new.tech@fixinh.vn", StaffRole.TECHNICIAN));

		assertTrue(created.staffId().startsWith("TEC-"));
		assertEquals(EmploymentStatus.ACTIVE, created.employmentStatus());
		assertNotNull(created.initialPassword());
		assertTrue(client.findStaff(created.staffId()).isPresent());
	}

	@Test
	void createStaffRejectsAnEmailAlreadyOnFile() {
		assertThrows(SmpDuplicateEmailException.class,
				() -> client.createStaff(createRequest("john.tran@fixinh.vn", StaffRole.TECHNICIAN)));
	}

	@Test
	void createStaffRejectsAnEmailAlreadyOnFileCaseInsensitively() {
		assertThrows(SmpDuplicateEmailException.class,
				() -> client.createStaff(createRequest("JOHN.TRAN@FIXINH.VN", StaffRole.TECHNICIAN)));
	}

	@Test
	void concurrentCreatesForTheSameEmailLetOnlyOneThrough() throws InterruptedException {
		int attempts = 12;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		List<Future<?>> futures = IntStream.range(0, attempts)
				.mapToObj(i -> pool.submit(() -> {
					ready.countDown();
					try {
						go.await();
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					try {
						client.createStaff(createRequest("race@fixinh.vn", StaffRole.TECHNICIAN));
						succeeded.incrementAndGet();
					} catch (SmpDuplicateEmailException ex) {
						rejected.incrementAndGet();
					}
				}))
				.collect(Collectors.toList());

		ready.await();
		go.countDown();
		for (Future<?> future : futures) {
			try {
				future.get(5, TimeUnit.SECONDS);
			} catch (Exception ex) {
				throw new AssertionError(ex);
			}
		}
		pool.shutdown();

		assertEquals(1, succeeded.get(), "exactly one concurrent create for the same email should win");
		assertEquals(attempts - 1, rejected.get());
		assertEquals(1, client.listStaff("race@fixinh.vn").size());
	}

	@Test
	void updateStaffThrowsForAnUnknownId() {
		assertThrows(java.util.NoSuchElementException.class,
				() -> client.updateStaff("TEC-999", updateRequest()));
	}

	@Test
	void updateStaffKeepsTheIdentityFieldsAndClearsTheInitialPassword() {
		SmpStaffDto before = client.findStaff("TEC-014").orElseThrow();

		SmpStaffDto updated = client.updateStaff("TEC-014", updateRequest());

		assertEquals(before.staffId(), updated.staffId());
		assertEquals(before.email(), updated.email());
		assertEquals(before.staffRole(), updated.staffRole());
		assertEquals(before.branchProvinceCode(), updated.branchProvinceCode());
		assertEquals("Updated Name", updated.fullName());
		assertEquals(EmploymentStatus.INACTIVE, updated.employmentStatus());
		assertNull(updated.initialPassword());
	}

	@Test
	void updateStaffLeavesEmploymentStatusUnchangedWhenNotGiven() {
		SmpStaffDto before = client.findStaff("TEC-014").orElseThrow();

		SmpStaffDto updated = client.updateStaff("TEC-014",
				new SmpStaffUpdateRequest("Updated Name", "0900000000", before.address(), null));

		assertEquals(before.employmentStatus(), updated.employmentStatus());
	}

	private SmpStaffCreateRequest createRequest(String email, StaffRole role) {
		return new SmpStaffCreateRequest("New Hire", email, "0900000000", role,
				new SmpAddress("1", "Le Loi", "Phuong Ben Thanh", "HCM"), "HCM");
	}

	private SmpStaffUpdateRequest updateRequest() {
		return new SmpStaffUpdateRequest("Updated Name", "0900000001",
				new SmpAddress("2", "Le Loi", "Phuong Ben Thanh", "HCM"), EmploymentStatus.INACTIVE);
	}
}
