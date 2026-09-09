package com.fixinh.sbp.modules.rating.dto.external;

import java.time.Instant;
import java.util.List;

/** Section 8.2: the submitted object with the deletion capability added. */
public record RatingDetailDto(String ratingId, String bookingRef, int overallScore,
		String reviewComment, List<RatingDto.SubcategoryScoreDto> subcategoryScores,
		List<String> evidenceUrls, Instant submittedAt, boolean canDelete) {}
