package com.fixinh.sbp.modules.customer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.customer.dto.internal.CompleteAddressCommand;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/customers/me")
@RequiredArgsConstructor
public class CustomerController {
	private final CustomerInternalService customers;

	@PutMapping("/address")
	public ResponseEntity<Void> completeAddress(
			@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody CompleteAddressCommand command) {
		customers.completeAddress(principal.id(), command);
		return ResponseEntity.noContent().build();
	}
}
