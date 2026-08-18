package com.fixinh.sbp.modules.auth.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input for completing Google OAuth user profile with address (Req 3.2.U3).
 *
 * <p>Google users sign in without an address. Before they can book a service, they must provide
 * their address so the system can assign them to a region and connect them with nearby providers.
 */
public record CompleteProfileCommand(
		@NotBlank(message = "Street number is required")
		@Size(max = 50, message = "Street number must be shorter than 50 characters")
		String streetNumber,

		@NotBlank(message = "Street name is required")
		@Size(max = 100, message = "Street name must be shorter than 100 characters")
		String streetName,

		@NotBlank(message = "Ward is required")
		@Size(max = 100, message = "Ward must be shorter than 100 characters")
		String ward,

		@NotBlank(message = "Province code is required")
		@Size(min = 2, max = 10, message = "Province code must be between 2 and 10 characters")
		String provinceCode
) {}
