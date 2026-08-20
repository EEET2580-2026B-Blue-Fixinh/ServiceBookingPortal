package com.fixinh.sbp.modules.admin.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixinh.sbp.common.smp.dto.EmploymentStatus;
import com.fixinh.sbp.common.smp.dto.SmpAddress;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.StaffRole;

/**
 * A staff record as SBP's own API reports it (API Documentation Sections 10.4–10.6).
 *
 * <p>Deliberately never carries {@link SmpStaffDto#initialPassword()} — Section 10.6 is explicit
 * that SBP relays it once into the welcome email and never returns it from an endpoint.
 * {@code address} is populated for a single-record read (10.5) and left null for a list row
 * (10.4); {@code notificationSent} is populated only on the response to a create (10.6).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffDto(
		String staffId,
		String fullName,
		String email,
		String phoneNumber,
		StaffRole staffRole,
		EmploymentStatus employmentStatus,
		String branchProvinceName,
		SmpAddress address,
		Boolean notificationSent) {

	public static StaffDto listItem(SmpStaffDto staff, String branchProvinceName) {
		return new StaffDto(staff.staffId(), staff.fullName(), staff.email(), staff.phoneNumber(),
				staff.staffRole(), staff.employmentStatus(), branchProvinceName, null, null);
	}

	public static StaffDto detail(SmpStaffDto staff, String branchProvinceName) {
		return new StaffDto(staff.staffId(), staff.fullName(), staff.email(), staff.phoneNumber(),
				staff.staffRole(), staff.employmentStatus(), branchProvinceName, staff.address(), null);
	}

	public static StaffDto created(SmpStaffDto staff, boolean notificationSent) {
		return new StaffDto(staff.staffId(), staff.fullName(), staff.email(), staff.phoneNumber(),
				staff.staffRole(), staff.employmentStatus(), null, null, notificationSent);
	}
}
