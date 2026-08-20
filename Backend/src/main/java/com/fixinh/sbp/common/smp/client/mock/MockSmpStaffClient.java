package com.fixinh.sbp.common.smp.client.mock;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.dto.EmploymentStatus;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;
import com.fixinh.sbp.common.smp.dto.StaffRole;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Standalone stand-in for SMP's staff API (Req 4.S2): seeded once from a bundled fixture file
 * (API Documentation Section 13.2), then held in memory — created/updated records do not
 * survive a restart.
 */
@Slf4j
public class MockSmpStaffClient implements SmpStaffClient {

	private static final String FIXTURE_PATH = "mocks/smp/staff.json";
	private static final Pattern ID_SUFFIX = Pattern.compile("(\\d+)$");
	private static final String PASSWORD_ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	private final Map<String, SmpStaffDto> staffById = Collections.synchronizedMap(new LinkedHashMap<>());
	private final Map<StaffRole, AtomicInteger> idSequenceByRole = Map.of(
			StaffRole.TECHNICIAN, new AtomicInteger(),
			StaffRole.RECEPTIONIST, new AtomicInteger());
	private final SecureRandom random = new SecureRandom();

	public MockSmpStaffClient(ObjectMapper objectMapper) {
		List<SmpStaffDto> seed = loadFixture(objectMapper);
		seed.forEach(staff -> {
			staffById.put(staff.staffId(), staff);
			idSequenceByRole.get(staff.staffRole()).updateAndGet(current -> Math.max(current, idSuffix(staff)));
		});
	}

	@Override
	public List<SmpStaffDto> listStaff() {
		synchronized (staffById) {
			return List.copyOf(staffById.values());
		}
	}

	@Override
	public Optional<SmpStaffDto> findStaff(String staffId) {
		return Optional.ofNullable(staffById.get(staffId));
	}

	@Override
	public SmpStaffDto createStaff(SmpStaffCreateRequest request) {
		boolean emailTaken = staffById.values().stream()
				.anyMatch(staff -> staff.email().equalsIgnoreCase(request.email()));
		if (emailTaken) {
			throw new SmpDuplicateEmailException(request.email());
		}

		SmpStaffDto created = new SmpStaffDto(
				nextStaffId(request.staffRole()),
				request.fullName(),
				request.email(),
				request.phoneNumber(),
				request.staffRole(),
				EmploymentStatus.ACTIVE,
				request.address(),
				request.branchProvinceCode(),
				generateInitialPassword());

		staffById.put(created.staffId(), created);
		return created;
	}

	@Override
	public SmpStaffDto updateStaff(String staffId, SmpStaffUpdateRequest request) {
		SmpStaffDto existing = staffById.get(staffId);
		if (existing == null) {
			throw new NoSuchElementException("No staff member with id " + staffId);
		}

		SmpStaffDto updated = new SmpStaffDto(
				existing.staffId(),
				request.fullName(),
				existing.email(),
				request.phoneNumber(),
				existing.staffRole(),
				request.employmentStatus() != null ? request.employmentStatus() : existing.employmentStatus(),
				request.address(),
				existing.branchProvinceCode(),
				// Never reissued: the initial password is a one-time value from creation.
				null);

		staffById.put(staffId, updated);
		return updated;
	}

	private String nextStaffId(StaffRole role) {
		String prefix = role == StaffRole.TECHNICIAN ? "TEC-" : "REC-";
		int next = idSequenceByRole.get(role).incrementAndGet();
		return prefix + String.format("%03d", next);
	}

	private String generateInitialPassword() {
		StringBuilder password = new StringBuilder(10);
		for (int i = 0; i < 10; i++) {
			password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
		}
		return password.toString();
	}

	private int idSuffix(SmpStaffDto staff) {
		var matcher = ID_SUFFIX.matcher(staff.staffId());
		return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
	}

	private List<SmpStaffDto> loadFixture(ObjectMapper objectMapper) {
		try (InputStream in = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
			return objectMapper.readValue(in, objectMapper.getTypeFactory()
					.constructCollectionType(List.class, SmpStaffDto.class));
		} catch (IOException ex) {
			log.error("Could not load {}; the mock SMP staff client starts empty.", FIXTURE_PATH, ex);
			return List.of();
		}
	}
}
