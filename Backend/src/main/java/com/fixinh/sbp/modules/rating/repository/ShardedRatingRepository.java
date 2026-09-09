package com.fixinh.sbp.modules.rating.repository;

import java.util.Optional;
import java.util.List;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.rating.model.Rating;

@Repository
public class ShardedRatingRepository implements RatingRepository {
	private final MongoTemplate south;
	private final MongoTemplate north;
	public ShardedRatingRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south; this.north = north;
	}
	@Override public Rating save(Rating rating) {
		if (rating.getHomeRegion() == null) throw new IllegalStateException("Rating.homeRegion must be set before saving.");
		return templateFor(rating.getHomeRegion()).save(rating);
	}
	@Override public Optional<Rating> findLiveByBookingRef(String bookingRef) {
		return find(Query.query(Criteria.where("bookingRef").is(bookingRef).and("deletedAt").is(null)));
	}
	@Override public Optional<Rating> findLiveById(String ratingId) {
		return find(Query.query(Criteria.where("_id").is(ratingId).and("deletedAt").is(null)));
	}
	@Override public boolean existsLiveByBookingRef(String bookingRef) { return findLiveByBookingRef(bookingRef).isPresent(); }
	@Override public List<Rating> findLiveByCustomerId(String customerId) {
		Query query = Query.query(Criteria.where("customerId").is(customerId).and("deletedAt").is(null));
		return java.util.stream.Stream.concat(
				south.find(query, Rating.class).stream(), north.find(query, Rating.class).stream())
				.sorted(Comparator.comparing(Rating::getSubmittedAt).reversed()).toList();
	}
	private Optional<Rating> find(Query query) {
		Rating value = south.findOne(query, Rating.class);
		if (value == null) value = north.findOne(query, Rating.class);
		return Optional.ofNullable(value);
	}
	private MongoTemplate templateFor(Region region) { return region == Region.SOUTH ? south : north; }
}
