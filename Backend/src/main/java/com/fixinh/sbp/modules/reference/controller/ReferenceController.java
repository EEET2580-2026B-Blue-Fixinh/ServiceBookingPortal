package com.fixinh.sbp.modules.reference.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.dto.ListResponse;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

import lombok.RequiredArgsConstructor;

/**
 * Base path /reference (API Documentation Section 5). Small, slow-changing lists that populate
 * dropdowns — read constantly, written almost never, so they are safe for the client to cache
 * for the length of a session.
 */
@RestController
@RequestMapping("/api/v1/reference")
@RequiredArgsConstructor
public class ReferenceController {

	private final ReferenceExternalService referenceService;

	/**
	 * Section 5.1. Public, because registration needs the address dropdown before anyone holds
	 * a token (Reqs 3.1.S3, 3.1.S4).
	 */
	@GetMapping("/provinces")
	public ListResponse<ProvinceDto> listProvinces() {
		return new ListResponse<>(referenceService.listProvinces());
	}
}
