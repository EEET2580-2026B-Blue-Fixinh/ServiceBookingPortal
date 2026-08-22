package com.fixinh.sbp.modules.customer.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteAddressCommand(
		@NotBlank @Size(max = 50) String streetNumber,
		@NotBlank @Size(max = 100) String streetName,
		@NotBlank @Size(max = 100) String ward,
		@NotBlank @Size(min = 2, max = 10) String provinceCode) {
}
