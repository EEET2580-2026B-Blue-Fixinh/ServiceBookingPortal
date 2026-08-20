package com.fixinh.sbp.common.smp.dto;

/** Fields sent to SMP on creation (API Documentation Section 10.6). */
public record SmpStaffCreateRequest(
		String fullName,
		String email,
		String phoneNumber,
		StaffRole staffRole,
		SmpAddress address,
		String branchProvinceCode) {
}
