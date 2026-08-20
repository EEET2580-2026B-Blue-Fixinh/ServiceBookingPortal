package com.fixinh.sbp.modules.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.dto.ListResponse;
import com.fixinh.sbp.modules.admin.dto.internal.AdminCustomerStatusCommand;
import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Base path /admin/customers, ADMIN-only (Req 3.5.S2, enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

	private final AdminInternalService adminService;

	@GetMapping
	public ListResponse<CustomerDto> listCustomers() {
		return new ListResponse<>(adminService.listCustomers());
	}

	@PatchMapping("/{customerId}/status")
	public CustomerDto setStatus(@PathVariable String customerId,
			@Valid @RequestBody AdminCustomerStatusCommand command) {
		return adminService.setCustomerStatus(customerId, command.status());
	}
}