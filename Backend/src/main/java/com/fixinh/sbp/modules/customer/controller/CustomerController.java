package com.fixinh.sbp.modules.customer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.customer.dto.external.CustomerProfileDto;
import com.fixinh.sbp.modules.customer.dto.internal.CompleteAddressCommand;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateProfileCommand;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/customers/me")
@RequiredArgsConstructor
public class CustomerController {
	private final CustomerInternalService customers;

	/** The signed-in customer's own profile, for the account page. */
	@GetMapping
	public CustomerProfileDto getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
		return customers.getMyProfile(principal.id());
	}

	/** Updates name, phone number and address (API Documentation Section 6.2). */
	@PutMapping
	public CustomerProfileDto updateMyProfile(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestBody UpdateProfileCommand command) {
		return customers.updateMyProfile(principal.id(), command);
	}

	@PutMapping("/address")
	public ResponseEntity<Void> completeAddress(
			@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody CompleteAddressCommand command) {
		customers.completeAddress(principal.id(), command);
		return ResponseEntity.noContent().build();
	}
}
