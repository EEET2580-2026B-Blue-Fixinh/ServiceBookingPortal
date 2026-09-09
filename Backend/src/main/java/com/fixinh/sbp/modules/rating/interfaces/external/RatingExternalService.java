package com.fixinh.sbp.modules.rating.interfaces.external;

import com.fixinh.sbp.modules.rating.dto.external.RatingDetailDto;
import com.fixinh.sbp.modules.rating.dto.external.RatingDto;
import com.fixinh.sbp.modules.rating.dto.internal.RatingSubmitCommand;
import java.util.List;

public interface RatingExternalService {
	RatingDto submit(String customerId, RatingSubmitCommand command);
	RatingDetailDto findByBooking(String customerId, String bookingRef);
	RatingDetailDto findById(String customerId, String ratingId);
	void delete(String customerId, String ratingId);
	List<RatingDto> listMine(String customerId);
}
