package com.fixinh.sbp.modules.booking.model;

/**
 * The eight statuses a booking can hold (API Documentation Appendix A, Req 3.3.M2).
 *
 * <p>The split into active and past is exactly this grouping and nothing derived from it — see
 * {@link #isActive()}, used by {@code GET /bookings} to sort a booking into one list or the
 * other (Req 3.3.M2).
 */
public enum BookingStatus {

	/** Submitted by the customer, not yet confirmed. The only status a new booking starts at. */
	CREATED(true),
	/** A receptionist has agreed price and time. Triggers a customer notification. */
	CONFIRMED(true),
	/** Technicians assigned and a time fixed. Triggers a customer notification. */
	SCHEDULED(true),
	/** The technician is on site. */
	WORKING(true),
	/** Work finished and the bill issued. Only a booking in this status may be rated. */
	COMPLETED(false),
	/** Cancelled by the customer from Created, Confirmed or Scheduled. */
	CANCELLED_BY_CUSTOMER(false),
	/** Stopped by the customer while Working — a technician was already on site. */
	TERMINATED_BY_CUSTOMER(false),
	/** Stopped by the technician. Set by SMP only; SBP never assigns this status itself. */
	ABORTED_BY_TECHNICIAN(false);

	private final boolean active;

	BookingStatus(boolean active) {
		this.active = active;
	}

	/** @return true for Created/Confirmed/Scheduled/Working, false for the four past statuses. */
	public boolean isActive() {
		return active;
	}
}
