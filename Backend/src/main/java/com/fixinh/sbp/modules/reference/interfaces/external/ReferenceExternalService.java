package com.fixinh.sbp.modules.reference.interfaces.external;

import java.util.List;

import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.dto.external.RepairServiceCatalogDto;
import com.fixinh.sbp.modules.reference.dto.external.RatingCategoryDto;

/**
 * The small, slow-changing lists that populate dropdowns (API Documentation Section 5).
 *
 * <p>Rating categories (5.3) belong to the rating module's own task.
 */
public interface ReferenceExternalService {

	/** Every province and municipality offered in the address dropdown (Reqs 3.1.S3, 3.1.S4). */
	List<ProvinceDto> listProvinces();

	/**
	 * The bookable services SBP has replicated from SMP (§5.2), for the service picker and the
	 * booking-list filter.
	 *
	 * @param activeOnly when true (the booking form's default), retired services are left out;
	 *                    when false, the filter panel can still name a service on an old booking.
	 */
	RepairServiceCatalogDto listRepairServices(boolean activeOnly);

	List<RatingCategoryDto> listRatingCategories();
}
