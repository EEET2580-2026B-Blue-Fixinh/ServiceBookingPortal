package com.fixinh.sbp.common.smp.client.mock;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

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
 *
 * <p>Wired by {@code fixinh.sbp.smp.staff-mode}, which defaults to {@code mock} even when
 * bookings are live: SMT's service has no staff API yet, so this is where every staff account
 * — and so every welcome email of Req 3.1.S5 — comes from until it does.
 */
@Slf4j
public class MockSmpStaffClient implements SmpStaffClient {

	private static final String FIXTURE_PATH = "mocks/smp/staff.json";
	private static final String PASSWORD_ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	/** Six hex digits: sixteen million ids per role. */
	private static final int ID_SPACE = 1 << 24;

	private final Map<String, SmpStaffDto> staffById = Collections.synchronizedMap(new LinkedHashMap<>());
	private final SecureRandom random = new SecureRandom();

	public MockSmpStaffClient(ObjectMapper objectMapper) {
		List<SmpStaffDto> seed = loadFixture(objectMapper);
		seed.forEach(staff -> staffById.put(staff.staffId(), staff));

		// Says which client answers staff calls. Without this, "is staff mocked while bookings
		// are live?" is only answerable by reading two properties across three files.
		log.info("SMP staff: MOCK mode. {} staff loaded from {}; staff created here are held in "
				+ "memory until the process stops", seed.size(), FIXTURE_PATH);
	}

	@Override
	public List<SmpStaffDto> listStaff(String query) {
		synchronized (staffById) {
			if (query == null || query.isBlank()) {
				return List.copyOf(staffById.values());
			}
			String needle = query.trim().toLowerCase(Locale.ROOT);
			return staffById.values().stream()
					.filter(staff -> matches(staff, needle))
					.toList();
		}
	}

	private boolean matches(SmpStaffDto staff, String needle) {
		return staff.fullName().toLowerCase(Locale.ROOT).contains(needle)
				|| staff.staffId().toLowerCase(Locale.ROOT).contains(needle)
				|| staff.email().toLowerCase(Locale.ROOT).contains(needle);
	}

	@Override
	public Optional<SmpStaffDto> findStaff(String staffId) {
		return Optional.ofNullable(staffById.get(staffId));
	}

	@Override
	public SmpStaffDto createStaff(SmpStaffCreateRequest request) {
		// The duplicate-email check and the insert must be one atomic step - synchronizing only
		// the check (or only the insert) still lets two concurrent requests for the same email
		// both pass the check before either inserts.
		synchronized (staffById) {
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

	/**
	 * A fresh id: the role's prefix and six random hex digits, such as {@code TEC-7F3A2C}.
	 *
	 * <p><b>Random rather than the next number.</b> This mock forgets what it created when the
	 * process stops; the Shard Directory in Atlas does not. A counter restarts from the
	 * fixture's highest id, so after a restart the next technician is {@code TEC-056} again —
	 * and the directory, which never moves a subject once placed, still files {@code TEC-056}'s
	 * notifications wherever the first one's branch was. Two developers on the shared clusters
	 * would hand out the same numbers at the same time, with the same result. A repeat across
	 * sixteen million values is not a concern for development data, and the check below rules
	 * one out within a run.
	 *
	 * <p>Called only while {@link #createStaff} holds the lock on {@code staffById}, so the
	 * check and the insert that follows cannot interleave with another create.
	 */
	private String nextStaffId(StaffRole role) {
		String prefix = role == StaffRole.TECHNICIAN ? "TEC-" : "REC-";
		String id;
		do {
			id = prefix + String.format("%06X", random.nextInt(ID_SPACE));
		} while (staffById.containsKey(id));
		return id;
	}

	private String generateInitialPassword() {
		StringBuilder password = new StringBuilder(10);
		for (int i = 0; i < 10; i++) {
			password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
		}
		return password.toString();
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
