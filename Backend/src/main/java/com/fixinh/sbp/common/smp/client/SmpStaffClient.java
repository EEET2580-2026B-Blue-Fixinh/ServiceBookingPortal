package com.fixinh.sbp.common.smp.client;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;

/**
 * Receptionist/Technician account management, normally owned by SMP (Req 3.5.S1). "mock"
 * serves local fixtures so admin can run standalone; "live" would call the real SMP API once
 * it exists (Req 4.S2) — switched by {@code fixinh.sbp.smp.mode}.
 */
public interface SmpStaffClient {

	/** query null or blank returns every staff member. */
	List<SmpStaffDto> listStaff(String query);

	Optional<SmpStaffDto> findStaff(String staffId);

	/** @throws SmpDuplicateEmailException if the email is already registered to a staff member */
	SmpStaffDto createStaff(SmpStaffCreateRequest request);

	/** @throws java.util.NoSuchElementException if no staff member has this id */
	SmpStaffDto updateStaff(String staffId, SmpStaffUpdateRequest request);
}
