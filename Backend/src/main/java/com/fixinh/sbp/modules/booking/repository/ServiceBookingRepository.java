package com.fixinh.sbp.modules.booking.repository;
import java.util.List;
import java.util.Optional;
import com.fixinh.sbp.common.sharding.Region;
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
	/**
	 * Every booking this customer has made (Req 3.3.S4), from the one store that owns them.
	 * Unfiltered and unsorted — a customer's own history is small enough that GET /bookings does
	 * its filtering, sorting, grouping and paging in the service layer rather than pushing a
	 * query builder down into this tier.
	 *
	 * @param homeRegion the customer's own region — resolved by the caller, same reasoning as
	 *                    {@link #save}.
	 */
	List<ServiceBooking> findByCustomerId(String customerId, Region homeRegion);
}
