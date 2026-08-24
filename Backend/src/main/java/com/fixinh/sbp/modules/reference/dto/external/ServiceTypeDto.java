package com.fixinh.sbp.modules.reference.dto.external;

/**
 * One bookable service (API Documentation Section 5.2).
 *
 * <p>{@code isActive} is named exactly like {@link ProvinceDto#isMunicipality()} rather than
 * {@code active}, so the serialized field matches the API doc's {@code "isActive"} key — Jackson
 * takes a record component's own name as the JSON property, unlike a classic bean getter.
 *
 * @param serviceId              SMP's catalogue identifier, e.g. {@code SVC-PLUMB-01} — what a
 *                                booking's {@code serviceId} field must match.
 * @param serviceName            display name for the service picker.
 * @param categoryName           groups entries in the filter panel, e.g. "Plumbing".
 * @param baseServiceFee         VND, no decimals (§2.6). Display only — the billed amount on a
 *                                completed booking comes from the booking itself (§7.3).
 * @param standardDurationMinutes typical job length, shown next to the fee.
 * @param isActive               false for a retired service — still returned, never omitted, so
 *                                a booking made before retirement can still show its name.
 */
public record ServiceTypeDto(
		String serviceId,
		String serviceName,
		String categoryName,
		long baseServiceFee,
		int standardDurationMinutes,
		boolean isActive) {
}
