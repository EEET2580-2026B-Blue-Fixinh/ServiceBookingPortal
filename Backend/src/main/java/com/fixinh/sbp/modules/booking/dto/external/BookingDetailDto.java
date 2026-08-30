package com.fixinh.sbp.modules.booking.dto.external;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;

/**
 * One booking in full — {@code GET /bookings/{bookingRef}} (API Documentation §7.3, Reqs 3.3.S4,
 * 3.3.S5, 3.3.S6).
 *
 * <p>Everything §7.2's list row leaves out is here: the issue the customer described, the time
 * window they offered, where the job is, and the bill. The list exists to be scanned; this
 * exists to be read.
 *
 * <p>{@link JsonInclude} drops absent fields entirely rather than publishing nulls, the same
 * contract §7.2 states — and §7.3 adds one of its own about {@code bill}: <i>"absent until the
 * technician issues it. Do not treat its absence as an error."</i>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingDetailDto(
		String bookingRef,
		BookingStatus status,
		String serviceId,
		String serviceName,
		String issueDescription,
		LocalDate preferredServiceDate,
		LocalTime availableStartTime,
		LocalTime availableEndTime,
		Instant createdAt,
		Instant scheduledStartAt,
		Integer estimatedDurationMinutes,
		List<TechnicianRef> technicians,
		AddressDto serviceAddress,
		BillDto bill,
		/** The cancellation this booking carries, if it was cancelled (§7.4, Req 3.3.M1). */
		CancellationDto cancellation,
		boolean hasRating,
		Instant lastSyncedAt) {

	/**
	 * §7.3 publishes {@code provinceName}, never {@code provinceCode}. The stored address holds
	 * only the code (see {@code Address}), so the name is resolved from reference data on the
	 * way out — which is the whole reason the name is not duplicated onto every booking.
	 */
	public record AddressDto(
			String streetNumber,
			String streetName,
			String ward,
			String provinceName) {
	}

	/**
	 * {@code totalCost} is {@code serviceFee + materialCost}, computed by {@code Bill} rather
	 * than carried alongside them, so the three can never contradict each other (Req 3.3.S6).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record BillDto(
			long serviceFee,
			long materialCost,
			long totalCost,
			String billImageUrl,
			Instant issuedAt) {
	}

	/**
	 * Not in §7.3's example, which predates the cancellation endpoint. Added because §7.4 stores
	 * a reason and evidence URLs and nothing else would ever show them: a customer who cancels
	 * with three photographs has no way back to what they wrote. Raised as a documentation
	 * discrepancy rather than left silently divergent.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CancellationDto(
			String reason,
			List<String> evidenceUrls,
			Instant cancelledAt) {
	}
}
