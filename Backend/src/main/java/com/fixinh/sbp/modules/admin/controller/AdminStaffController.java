package com.fixinh.sbp.modules.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.dto.ListResponse;
import com.fixinh.sbp.modules.admin.dto.external.StaffDto;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;
import com.fixinh.sbp.modules.admin.dto.internal.StaffUpdateCommand;
import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Base path /admin/staff, ADMIN-only (Req 3.5.S1, enforced in SecurityConfig). SBP holds no
 * staff records — everything here is relayed to/from SMP (API Documentation Section 10.4).
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
@RequiredArgsConstructor
public class AdminStaffController {

	private final AdminInternalService adminService;

	@GetMapping
	public ListResponse<StaffDto> listStaff() {
		return new ListResponse<>(adminService.listStaff());
	}

	@GetMapping("/{staffId}")
	public StaffDto getStaff(@PathVariable String staffId) {
		return adminService.getStaff(staffId);
	}

	@PostMapping
	public ResponseEntity<StaffDto> createStaff(@Valid @RequestBody StaffCreateCommand command) {
		StaffDto created = adminService.createStaff(command);
		return ResponseEntity.status(201).body(created);
	}

	@PutMapping("/{staffId}")
	public StaffDto updateStaff(@PathVariable String staffId,
			@Valid @RequestBody StaffUpdateCommand command) {
		return adminService.updateStaff(staffId, command);
	}
}
