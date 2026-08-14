package com.fixinh.sbp.modules.reference.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;
import com.fixinh.sbp.modules.reference.model.Province;

/**
 * Serves the reference lists from the in-code catalogue.
 *
 * <p>No database read: this list changes at most once per administrative reform, and the same
 * table decides shard routing, so keeping it in code means the dropdown and the router can
 * never disagree. If it ever needs editing without a release, move it behind
 * {@code ProvinceRepository} — this class is the only thing that would change.
 */
@Service
public class ReferenceServiceImpl implements ReferenceExternalService {

	/** Built once: the catalogue is immutable for the life of the process. */
	private static final List<ProvinceDto> PROVINCES = Arrays.stream(Province.values())
			.map(province -> new ProvinceDto(
					province.getProvinceCode(),
					province.getDisplayName(),
					province.isMunicipality(),
					province.getOfficialCode()))
			// Alphabetical by the name the customer actually reads, not by internal code order.
			.sorted(Comparator.comparing(ProvinceDto::provinceName))
			.toList();

	@Override
	public List<ProvinceDto> listProvinces() {
		return PROVINCES;
	}
}
