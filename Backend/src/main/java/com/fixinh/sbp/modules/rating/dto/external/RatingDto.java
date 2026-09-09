package com.fixinh.sbp.modules.rating.dto.external;

import java.time.Instant;
import java.util.List;

public record RatingDto(String ratingId, String bookingRef, int overallScore, String reviewComment,
		List<SubcategoryScoreDto> subcategoryScores, List<String> evidenceUrls, Instant submittedAt) {
	public record SubcategoryScoreDto(String categoryName, int score) {}
}
