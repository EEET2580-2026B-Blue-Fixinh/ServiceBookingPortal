package com.fixinh.mocksmp.staff.dto;

import jakarta.validation.constraints.NotBlank;

public record Address(
		@NotBlank String streetNumber,
		@NotBlank String streetName,
		@NotBlank String ward,
		@NotBlank String provinceCode) {
}
