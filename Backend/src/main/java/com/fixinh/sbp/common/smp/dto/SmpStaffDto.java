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

	/**
	 * <b>Redacts the password.</b> A record's generated {@code toString()} prints every
	 * component, so one careless log statement would put a staff member's initial password in
	 * the application log. See {@code StaffWelcomeEmailCommand#toString()} for the same fix.
	 */
	@Override
	public String toString() {
		return "SmpStaffDto[staffId=%s, fullName=%s, email=%s, phoneNumber=%s, staffRole=%s, "
				+ "employmentStatus=%s, address=%s, branchProvinceCode=%s, "
				+ "initialPassword=<redacted>]"
						.formatted(staffId, fullName, email, phoneNumber, staffRole,
								employmentStatus, address, branchProvinceCode);
	}
}
