package com.fixinh.sbp.common.smp.dto;

/**
 * One entry from SMP's repair-service catalogue (API Documentation §12.5), in the shape SBP's
 * local replica needs — for {@code GET /reference/repair-services} and for validating and
 * pricing a new booking (§7.1).
 *
 * <p>{@code active} entries are still returned when false rather than omitted, so a booking made
 * before a service was retired can still show its name (§5.2).
 */
public record SmpRepairServiceDto(
		String serviceId,
		String serviceName,
		String categoryName,
		long baseServiceFee,
		int standardDurationMinutes,
		boolean active) {
}
