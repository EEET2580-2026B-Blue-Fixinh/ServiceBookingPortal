package com.fixinh.sbp.common.smp.client;

import java.util.List;

import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;

/**
 * What SBP needs from SMP for the booking domain (API Documentation §12.1 create a booking,
 * §12.5 repair-service catalogue) — both provisional until Team SMT publishes its own contract.
 *
 * <p>Mock and live implementations sit behind this one interface (§13.1); nothing else in the
 * codebase knows which is active. Only
 * {@link com.fixinh.sbp.common.smp.client.mock.MockSmpBookingClient} exists today — §12 is
 * explicitly "provisional, do not treat as agreed", so there is nothing yet for a live
 * implementation to call.
 */
public interface SmpBookingClient {

	/** The current catalogue, active and inactive services alike. */
	List<SmpRepairServiceDto> listRepairServices();

	/**
	 * Forwards a booking a customer just submitted. SMP owns the record and generates
	 * {@code bookingRef}; SBP keeps only a projection of it (§7, "Behaviour when SMP is
	 * unavailable").
	 */
	SmpBookingDto createBooking(String customerId, String serviceId);
}
