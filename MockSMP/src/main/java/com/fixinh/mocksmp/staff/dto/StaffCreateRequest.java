package com.fixinh.mocksmp.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StaffCreateRequest(
		@NotBlank String fullName,
		@NotBlank String email,
		@NotBlank String phoneNumber,
		@NotNull StaffRole staffRole,
		@NotNull @Valid Address address,
		@NotBlank String branchProvinceCode) {
}
