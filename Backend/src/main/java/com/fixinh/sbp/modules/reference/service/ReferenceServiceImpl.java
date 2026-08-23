package com.fixinh.sbp.modules.reference.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.dto.external.RepairServiceCatalogDto;
import com.fixinh.sbp.modules.reference.dto.external.ServiceTypeDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;
import com.fixinh.sbp.modules.reference.model.Province;
import com.fixinh.sbp.modules.reference.model.RepairServiceType;
import com.fixinh.sbp.modules.reference.repository.RepairServiceTypeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Serves the reference lists (API Documentation Section 5).
 *
 * <p>Provinces come from the in-code catalogue — see the {@link #PROVINCES} note. Repair
 * services come from Mongo: {@link RepairServiceTypeRepository} holds SBP's replica of SMP's
 * catalogue, kept current by {@link RepairServiceCatalogSeeder}.
 */
@Service
@RequiredArgsConstructor
public class ReferenceServiceImpl implements ReferenceExternalService {

	/**
	 * Built once: the catalogue is immutable for the life of the process. No database read —
	 * this list changes at most once per administrative reform, and the same table decides shard
	 * routing, so keeping it in code means the dropdown and the router can never disagree. If it
	 * ever needs editing without a release, move it behind a repository — this class is the only
	 * thing that would change.
	 */
	private static final List<ProvinceDto> PROVINCES = Arrays.stream(Province.values())
			.map(province -> new ProvinceDto(
					province.getProvinceCode(),
					province.getDisplayName(),
					province.isMunicipality(),
					province.getOfficialCode()))
			// Alphabetical by the name the customer actually reads, not by internal code order.
			.sorted(Comparator.comparing(ProvinceDto::provinceName))
			.toList();

	private final RepairServiceTypeRepository repairServiceTypeRepository;
	private final RepairServiceCatalogSyncStatus syncStatus;

	@Override
	public List<ProvinceDto> listProvinces() {
		return PROVINCES;
	}

	@Override
	public RepairServiceCatalogDto listRepairServices(boolean activeOnly) {
		List<ServiceTypeDto> items = repairServiceTypeRepository.findAll().stream()
				.filter(service -> !activeOnly || service.isActive())
				.map(ReferenceServiceImpl::toDto)
				// Alphabetical by name, same reasoning as the province list above.
				.sorted(Comparator.comparing(ServiceTypeDto::serviceName))
				.toList();

		return new RepairServiceCatalogDto(items, syncStatus.lastSyncedAt());
	}

	private static ServiceTypeDto toDto(RepairServiceType service) {
		return new ServiceTypeDto(
				service.getServiceId(),
				service.getServiceName(),
				service.getCategoryName(),
				service.getBaseServiceFee(),
				service.getStandardDurationMinutes(),
				service.isActive());
	}
}
