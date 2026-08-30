package com.fixinh.sbp.common.smp.dto;

import java.time.Instant;
import java.util.List;

/**
 * What SBP sends SMP when a customer cancels (API Documentation §12.4).
 *
 * <p>{@code evidenceUrls} carries links, never bytes — SBP has already uploaded the images and
 * §7.4 is explicit that no image crosses this boundary.
 *
 * @param actorType  always {@code CUSTOMER} for a call from SBP; §12.4 asks for it anyway,
 *                   because SMP receives cancellations from its own staff too
 * @param cancelledAt when the customer acted, not when this call was made
 */
public record SmpCancellationRequest(
		String reason,
		List<String> evidenceUrls,
		String actorType,
		Instant cancelledAt) {

	/** The only actor SBP can ever be. */
	private static final String CUSTOMER = "CUSTOMER";

	public static SmpCancellationRequest byCustomer(
			String reason, List<String> evidenceUrls, Instant cancelledAt) {
		return new SmpCancellationRequest(reason, evidenceUrls, CUSTOMER, cancelledAt);
	}
}
