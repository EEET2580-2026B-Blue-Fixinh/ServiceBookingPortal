package com.fixinh.sbp.modules.rating.dto.internal;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record RatingSubmitCommand(String bookingRef, Integer overallScore, String reviewComment,
		List<SubcategoryScoreCommand> subcategoryScores, List<MultipartFile> evidence) {
	public record SubcategoryScoreCommand(String categoryName, Integer score) {}
}
