package com.fixinh.mocksmp.staff;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.mocksmp.common.ListResponse;
import com.fixinh.mocksmp.staff.dto.Staff;
import com.fixinh.mocksmp.staff.dto.StaffCreateRequest;
import com.fixinh.mocksmp.staff.dto.StaffUpdateRequest;

import jakarta.validation.Valid;

/** docs/smp-staff-contract.md is the contract this implements. */
@RestController
@RequestMapping("/staff")
public class StaffController {

	private final StaffStore store;

	public StaffController(StaffStore store) {
		this.store = store;
	}

	@GetMapping
	public ListResponse<Staff> list(@RequestParam(required = false) String q) {
		return new ListResponse<>(store.list(q));
	}

	@GetMapping("/{staffId}")
	public Staff detail(@PathVariable String staffId) {
		return store.find(staffId);
	}

	@PostMapping
	public ResponseEntity<Staff> create(@Valid @RequestBody StaffCreateRequest request) {
		return ResponseEntity.status(201).body(store.create(request));
	}

	@PutMapping("/{staffId}")
	public Staff update(@PathVariable String staffId, @Valid @RequestBody StaffUpdateRequest request) {
		return store.update(staffId, request);
	}
}
