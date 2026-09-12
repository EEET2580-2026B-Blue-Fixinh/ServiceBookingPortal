package com.fixinh.sbp.modules.booking.interfaces.internal;

import com.fixinh.sbp.modules.booking.dto.internal.BookingProjectionRefresh;
import com.fixinh.sbp.modules.booking.dto.internal.BookingRatingView;

/** Booking facts another SBP module may consume without accessing booking persistence. */
public interface BookingInternalService {

	BookingRatingView findOwnedForRating(String customerId, String bookingRef);

	/**
	 * Brings SBP's projection of a booking back in line with SMP (§2.8, §11.7, Req 4.M3).
	 *
	 * <p>A merge, not a replacement. Only what SMP sent is written; a field SMP omitted keeps
	 * whatever SBP already held, because §11.7's payload carries the fields relevant to the
	 * change and not a complete booking — reading an absent field as an erasure would blank a
	 * scheduled visit on the next status change and take Req 3.3.S5's technician name with it.
	 *
	 * <p>The write is complete when this returns, which is what lets the caller publish an
	 * event whose listeners can rely on the refreshed state.
	 *
	 * @param bookingRef the booking to refresh, as SMP and every URL name it
	 * @param refresh what SMP says it now looks like
	 * @return the refreshed booking's status and schedule — enough for the caller to log and
	 *         to decide what to publish, without handing another module the document itself
	 * @throws com.fixinh.sbp.common.exception.ApiException 404 {@code BOOKING_NOT_FOUND} when
	 *         SBP has no projection of this booking. SBP does not create one from an event:
	 *         every booking SBP knows was created through SBP, and an unknown reference means
	 *         the two systems disagree about something a silent insert would hide
	 */
	BookingProjectionView refreshProjection(String bookingRef, BookingProjectionRefresh refresh);
}