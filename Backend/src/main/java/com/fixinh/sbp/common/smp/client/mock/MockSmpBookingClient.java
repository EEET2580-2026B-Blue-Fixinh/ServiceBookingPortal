package com.fixinh.sbp.common.smp.client.mock;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fixinh.sbp.common.smp.client.LocalCancellationRule;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpBookingCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationRequest;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;
import com.fixinh.sbp.common.smp.dto.SmpRatingBookingDto;

/**
 * Stands in for SMP until Team SMT's API is available (API Documentation §13, Req 4.S2). A fixed
 * catalogue and an always-succeeding booking creation — enough to develop and demo the booking
 * flow end to end. Swapped for a live implementation behind {@link SmpBookingClient} once SMP,
 * and its real prices, exist; nothing outside the two implementations needs to change.
 *
 * <p>Registered by {@code SmpClientConfig} under {@code fixinh.sbp.smp.mode=mock} rather than by
 * component scan, so that setting the mode to {@code live} actually takes this bean out.
 */
public class MockSmpBookingClient implements SmpBookingClient {

	/**
	 * The five categories the booking screens draw icons for
	 * (Frontend/src/components/booking/bookingPresentation.js), so a booking made against this
	 * catalogue renders with an icon rather than the fallback. Placeholder fees — correct once
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

	/** The four digits §2.6's ORD-YYYY-NNNN format allows, so the reference never gains a fifth. */
	private static final int MIN_SEQUENCE = 1000;
	private static final int MAX_SEQUENCE = 9999;

	/**
	 * Seeded from the clock rather than from a constant, <b>because the store outlives the
	 * process</b>.
	 *
	 * <p>A fixed start of 1000 meant every restart began reissuing ORD-YYYY-1001 onward — and
	 * those references were already in Mongo under a unique index on {@code bookingRef}, so the
	 * first booking of the second run died on a duplicate key. A real SMP owns a durable
	 * sequence; a mock has only the current time.
	 *
	 * <p>Two runs started inside the same minute still begin at the same number, which is why
	 * the caller does not take a reference on trust — see {@code BookingServiceImpl}.
	 */
	private final AtomicInteger sequence = new AtomicInteger(seedFromClock());

	@Override
	public List<SmpRepairServiceDto> listRepairServices() {
		return CATALOGUE;
	}

	@Override
	public SmpBookingDto createBooking(SmpBookingCreateRequest request) {
		return new SmpBookingDto(nextReference(), "CREATED", Instant.now());
	}

	/** Wraps rather than overflowing: 9999 is followed by 1000, never by a five-digit 10000. */
	private String nextReference() {
		int next = sequence.updateAndGet(
				current -> current >= MAX_SEQUENCE ? MIN_SEQUENCE : current + 1);
		return "ORD-%d-%04d".formatted(Year.now().getValue(), next);
	}

	/** Minutes since the epoch, folded into the range the format allows. */
	private static int seedFromClock() {
		long minutes = Instant.now().getEpochSecond() / 60;
		return (int) (minutes % (MAX_SEQUENCE - MIN_SEQUENCE)) + MIN_SEQUENCE;
	}

	/**
	 * The three scenarios §13.2 requires of the cancellation mock — cancel from Scheduled,
	 * terminate from Working, and a refusal from Completed — expressed as the rule §12.4 puts on
	 * SMP's side rather than as fixture rows, because the rule is total: every one of Appendix
	 * A's eight statuses has exactly one answer here. The live client answers from the same
	 * {@link LocalCancellationRule}, because SMT has published no cancellation endpoint to ask.
	 */
	@Override
	public SmpCancellationDto cancelBooking(
			String bookingRef, String currentStatus, SmpCancellationRequest request) {

		return LocalCancellationRule.apply(bookingRef, currentStatus, request.cancelledAt());
	}

	@Override
	public SmpRatingBookingDto findBookingForRating(SmpRatingBookingDto localProjection) {
		// SMT has not published GET /bookings/{bookingRef}. In mock mode, SBP's synced
		// projection is the fixture; this keeps the rating flow usable once a booking-status
		// fixture/event marks a booking COMPLETED.
		return localProjection;
	}
}
