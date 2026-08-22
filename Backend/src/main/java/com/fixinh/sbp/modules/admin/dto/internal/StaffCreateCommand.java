package com.fixinh.sbp.modules.admin.dto.internal;

import com.fixinh.sbp.common.smp.dto.SmpAddress;
import com.fixinh.sbp.common.smp.dto.StaffRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /admin/staff body (API Documentation Section 10.6). Only presence is checked here — the
 * same field-content rules as customer registration (Appendix C) apply, and are enforced in the
 * service layer, matching {@code RegistrationValidator}.
 */
public record StaffCreateCommand(

		@NotBlank(message = "Enter the staff member's full name.")
		String fullName,

		@NotBlank(message = "Enter the staff member's email.")
		String email,

		@NotBlank(message = "Enter the staff member's phone number.")
		String phoneNumber,

		@NotNull(message = "Choose RECEPTIONIST or TECHNICIAN.")
		StaffRole staffRole,

		@NotNull(message = "Enter the staff member's address.")
		SmpAddress address,

		@NotBlank(message = "Choose the branch this staff member belongs to.")
		String branchProvinceCode) {
}
