package com.fixinh.sbp.common.smp.dto;

/**
 * A Receptionist or Technician account, as SMP reports it (API Documentation Sections
 * 10.4–10.7, 12.8).
 */
public record SmpStaffDto(
		String staffId,
		String fullName,
		String email,
		String phoneNumber,
		StaffRole staffRole,
		EmploymentStatus employmentStatus,
		SmpAddress address,
		String branchProvinceCode,
		/**
		 * Set by SMP on creation and delivered nowhere else — SBP relays it once, into the
		 * welcome email (Req 3.1.S5), and never returns it from an SBP endpoint. Null on every
		 * response except the one immediately after {@code createStaff}.
		 */
		String initialPassword) {
}
