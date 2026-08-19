package com.fixinh.sbp.modules.booking.repository;

import java.util.Optional;

import com.fixinh.sbp.modules.booking.model.ServiceBooking;

/**
 * Booking data access spanning both regional stores (mirrors {@code CustomerRepository}).
 *
 * <p>Deliberately a plain interface rather than a Spring Data {@code MongoRepository}, for the
 * same reason as the customer store: a derived query binds to one {@code MongoTemplate}, and a
 * booking's shard is decided by its owning customer, not by anything Spring Data can infer.
 */
public interface ServiceBookingRepository {

	/**
	 * Persists to {@code booking.getHomeRegion()}. The caller — the service layer — must resolve
	 * and set that region first; this tier does not infer it, since doing so would mean reaching
	 * into another module's data from the repository layer.
	 */
	ServiceBooking save(ServiceBooking booking);

	/** Whether a bookingRef is already taken, checked across both stores before it is assigned. */
	boolean existsByBookingRef(String bookingRef);

	/** Looked up by reference rather than the internal id — bookingRef is what a URL carries. */
	Optional<ServiceBooking> findByBookingRef(String bookingRef);
}
