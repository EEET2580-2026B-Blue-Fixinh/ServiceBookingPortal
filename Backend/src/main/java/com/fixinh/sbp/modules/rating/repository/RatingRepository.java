package com.fixinh.sbp.modules.rating.repository;

import java.util.Optional;
import java.util.List;
import com.fixinh.sbp.modules.rating.model.Rating;

public interface RatingRepository {
	Rating save(Rating rating);
	Optional<Rating> findLiveByBookingRef(String bookingRef);
	Optional<Rating> findLiveById(String ratingId);
	boolean existsLiveByBookingRef(String bookingRef);
	List<Rating> findLiveByCustomerId(String customerId);
}
