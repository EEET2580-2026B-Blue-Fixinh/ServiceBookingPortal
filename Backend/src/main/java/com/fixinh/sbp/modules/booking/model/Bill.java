package com.fixinh.sbp.modules.booking.model;

import java.time.Instant;

/**
 * What a completed booking cost (Req 3.3.S6, API Documentation §7.3).
 *
 * <p>Embedded in {@link ServiceBooking}, never its own collection — a bill belongs to exactly
 * one booking and is never read without it.
 *
 * <p><b>{@code totalCost} is computed, not stored.</b> Req 3.3.S6 states Total Cost = Service
 * Fee + Material Cost, and §7.3 says the total "is calculated server-side so the two sides
 * cannot disagree". A stored third number could drift from the two it is made of; a derived one
 * cannot. SMP sends its own total across the integration boundary, and §12.3 asks SBP to
 * recompute rather than trust it — the recomputation is this method, and the comparison happens
 * where the event arrives.
 *
 * @param billImageUrl the photographed bill the technician uploaded, or null before they do
 * @param issuedAt when the technician issued it
 */
public record Bill(
		long serviceFee,
		long materialCost,
		String billImageUrl,
		Instant issuedAt) {

	/** Req 3.3.S6. */
	public long totalCost() {
		return serviceFee + materialCost;
	}
}
