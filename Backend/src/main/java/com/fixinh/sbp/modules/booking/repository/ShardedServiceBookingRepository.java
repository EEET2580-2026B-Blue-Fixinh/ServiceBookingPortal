package com.fixinh.sbp.modules.booking.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;

/**
 * Reads and writes bookings across the north and south stores.
 *
 * <p><b>Each booking lives in exactly one shard</b> — the same one that owns the customer who
 * made it (see {@code ServiceBooking.homeRegion}). Unlike the customer store, a lookup by
 * {@code bookingRef} has no cheaper path: the reference carries no region either, so every read
 * here pays the same two-store cost {@code ShardedCustomerRepository} pays for sign-in.
 */
@Repository
public class ShardedServiceBookingRepository implements ServiceBookingRepository {

	private final MongoTemplate south;
	private final MongoTemplate north;

	public ShardedServiceBookingRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south;
		this.north = north;
	}

	@Override
	public ServiceBooking save(ServiceBooking booking) {
		Region home = booking.getHomeRegion();
		if (home == null) {
			throw new IllegalStateException(
					"ServiceBooking.homeRegion must be resolved before saving — this repository "
							+ "does not infer it from the customer or the service address.");
		}
		return templateFor(home).save(booking);
	}

	@Override
	public boolean existsByBookingRef(String bookingRef) {
		Query query = Query.query(Criteria.where("bookingRef").is(bookingRef));
		return south.exists(query, ServiceBooking.class) || north.exists(query, ServiceBooking.class);
	}

	@Override
	public Optional<ServiceBooking> findByBookingRef(String bookingRef) {
		Query query = Query.query(Criteria.where("bookingRef").is(bookingRef));

		ServiceBooking found = south.findOne(query, ServiceBooking.class);
		if (found == null) {
			found = north.findOne(query, ServiceBooking.class);
		}
		return Optional.ofNullable(found);
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}
}
