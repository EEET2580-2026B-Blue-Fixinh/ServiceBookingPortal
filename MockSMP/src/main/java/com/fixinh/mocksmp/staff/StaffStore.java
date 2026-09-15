package com.fixinh.mocksmp.staff;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.fixinh.mocksmp.staff.dto.EmploymentStatus;
import com.fixinh.mocksmp.staff.dto.Staff;
import com.fixinh.mocksmp.staff.dto.StaffCreateRequest;
import com.fixinh.mocksmp.staff.dto.StaffRole;
import com.fixinh.mocksmp.staff.dto.StaffUpdateRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole "database": seeded once from the bundled fixture, held in memory for the life of
 * the process. Ported from SBP's own MockSmpStaffClient so behaviour matches what SBP already
 * expects, now reachable over HTTP instead of in-process.
 */
@Component
public class StaffStore {

	private static final Logger log = LoggerFactory.getLogger(StaffStore.class);
	private static final String FIXTURE_PATH = "staff.json";
	private static final String PASSWORD_ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	/** Six hex digits: sixteen million ids per role. */
	private static final int ID_SPACE = 1 << 24;

	private final Map<String, Staff> staffById = Collections.synchronizedMap(new LinkedHashMap<>());
	private final SecureRandom random = new SecureRandom();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public StaffStore() {
		List<Staff> seed = loadFixture();
		seed.forEach(staff -> staffById.put(staff.staffId(), staff));
		log.info("Mock SMP staff store seeded with {} staff from {}", seed.size(), FIXTURE_PATH);
	}

	public List<Staff> list(String query) {
		synchronized (staffById) {
			if (query == null || query.isBlank()) {
				return List.copyOf(staffById.values());
			}
			String needle = query.trim().toLowerCase(Locale.ROOT);
			return staffById.values().stream().filter(staff -> matches(staff, needle)).toList();
		}
	}

	private boolean matches(Staff staff, String needle) {
		return staff.fullName().toLowerCase(Locale.ROOT).contains(needle)
				|| staff.staffId().toLowerCase(Locale.ROOT).contains(needle)
				|| staff.email().toLowerCase(Locale.ROOT).contains(needle);
	}

	public Staff find(String staffId) {
		Staff staff = staffById.get(staffId);
		if (staff == null) {
			throw new StaffNotFoundException(staffId);
		}
		return staff;
	}

	public Staff create(StaffCreateRequest request) {
		synchronized (staffById) {
			boolean emailTaken = staffById.values().stream()
					.anyMatch(staff -> staff.email().equalsIgnoreCase(request.email()));
			if (emailTaken) {
				throw new DuplicateEmailException(request.email());
			}

			Staff created = new Staff(
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

	public Staff update(String staffId, StaffUpdateRequest request) {
		synchronized (staffById) {
			Staff existing = staffById.get(staffId);
			if (existing == null) {
				throw new StaffNotFoundException(staffId);
			}

			Staff updated = new Staff(
					existing.staffId(),
					request.fullName(),
					existing.email(),
					request.phoneNumber(),
					existing.staffRole(),
					request.employmentStatus() != null ? request.employmentStatus() : existing.employmentStatus(),
					request.address(),
					existing.branchProvinceCode(),
					null);

			staffById.put(staffId, updated);
			return updated;
		}
	}

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

	private List<Staff> loadFixture() {
		try (InputStream in = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
			return objectMapper.readValue(in, objectMapper.getTypeFactory()
					.constructCollectionType(List.class, Staff.class));
		} catch (IOException ex) {
			log.error("Could not load {}; the staff store starts empty.", FIXTURE_PATH, ex);
			return List.of();
		}
	}
}
