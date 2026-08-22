package com.fixinh.sbp.common.smp.client.mock;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;

/**
 * Stands in for SMP until Team SMT's API is available (API Documentation §13, Req 4.S2). A fixed
 * catalogue and an always-succeeding booking creation — enough to develop and demo the booking
 * flow end to end. Swapped for a live implementation behind {@link SmpBookingClient} once SMP,
 * and its real prices, exist; nothing outside the two implementations needs to change.
 */
@Component
public class MockSmpBookingClient implements SmpBookingClient {

	/**
	 * Mirrors the five categories the frontend already renders
	 * (Frontend/src/pages/booking/mockBookings.js), so the mock catalogue and the mock UI agree
	 * while SMT's real services and prices are still unknown. Placeholder fees — correct once
	 * §12.5 is a real endpoint.
	 */
	private static final List<SmpRepairServiceDto> CATALOGUE = List.of(
			new SmpRepairServiceDto("SVC-PLUMB-01", "Plumbing Repair", "Plumbing",
					500_000, 120, true),
			new SmpRepairServiceDto("SVC-AC-01", "Air Conditioning Service", "Air Conditioning",
					450_000, 90, true),
			new SmpRepairServiceDto("SVC-ELEC-01", "Electrical Repair", "Electrical",
					400_000, 90, true),
			new SmpRepairServiceDto("SVC-ELECTR-01", "Electronics Repair", "Electronics Repair",
					350_000, 60, true),
			new SmpRepairServiceDto("SVC-APPL-01", "Home Appliance Repair", "Home Appliance",
					380_000, 90, true));

	/** Unique only within one running process — real uniqueness comes from SMP once it exists. */
	private final AtomicInteger sequence = new AtomicInteger(1000);

	@Override
	public List<SmpRepairServiceDto> listRepairServices() {
		return CATALOGUE;
	}

	@Override
	public SmpBookingDto createBooking(String customerId, String serviceId) {
		String bookingRef = "ORD-%d-%04d".formatted(Year.now().getValue(), sequence.incrementAndGet());
		return new SmpBookingDto(bookingRef, "CREATED", Instant.now());
	}
}
