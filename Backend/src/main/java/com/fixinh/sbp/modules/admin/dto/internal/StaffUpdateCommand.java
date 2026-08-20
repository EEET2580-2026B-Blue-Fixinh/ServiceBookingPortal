package com.fixinh.sbp.modules.admin.dto.internal;

import com.fixinh.sbp.common.smp.dto.EmploymentStatus;
import com.fixinh.sbp.common.smp.dto.SmpAddress;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /admin/staff/{staffId} body (API Documentation Section 10.7). Email and staffRole are not
 * editable in this release, so they are absent here.
 */
public record StaffUpdateCommand(

		@NotBlank(message = "Enter the staff member's full name.")
		String fullName,

		@NotBlank(message = "Enter the staff member's phone number.")
		String phoneNumber,

		@NotNull(message = "Enter the staff member's address.")
		SmpAddress address,

		/** Optional — null leaves employment status unchanged. */
		EmploymentStatus employmentStatus) {
}
