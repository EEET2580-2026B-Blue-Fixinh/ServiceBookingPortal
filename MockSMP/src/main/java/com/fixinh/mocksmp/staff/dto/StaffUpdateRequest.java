package com.fixinh.mocksmp.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Email and role cannot be changed. employmentStatus null leaves the status unchanged. */
public record StaffUpdateRequest(
		@NotBlank String fullName,
		@NotBlank String phoneNumber,
		@NotNull @Valid Address address,
		EmploymentStatus employmentStatus) {
}
