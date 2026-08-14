package com.fixinh.sbp.modules.reference.interfaces.external;

import java.util.List;

import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;

/**
 * The small, slow-changing lists that populate dropdowns (API Documentation Section 5).
 *
 * <p>Only provinces are served today. Repair services (5.2) and rating categories (5.3) belong
 * to the tasks that own the SMP catalogue sync and the rating module.
 */
public interface ReferenceExternalService {

	/** Every province and municipality offered in the address dropdown (Reqs 3.1.S3, 3.1.S4). */
	List<ProvinceDto> listProvinces();
}
