package com.fixinh.mocksmp.staff.dto;

/**
 * A staff member, in the shape docs/smp-staff-contract.md defines for SBP's SmpStaffDto.
 * {@code initialPassword} is set on creation and null everywhere else.
 */
public record Staff(
		String staffId,
		String fullName,
		String email,
		String phoneNumber,
		StaffRole staffRole,
		EmploymentStatus employmentStatus,
		Address address,
		String branchProvinceCode,
		String initialPassword) {

	// Redacts the password so a stray log statement can't print it.
	@Override
	public String toString() {
		return "Staff[staffId=%s, fullName=%s, email=%s, phoneNumber=%s, staffRole=%s, "
				+ "employmentStatus=%s, address=%s, branchProvinceCode=%s, initialPassword=<redacted>]"
						.formatted(staffId, fullName, email, phoneNumber, staffRole,
								employmentStatus, address, branchProvinceCode);
	}
}
