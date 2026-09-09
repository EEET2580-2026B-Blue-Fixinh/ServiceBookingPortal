package com.fixinh.sbp.modules.rating.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.rating.dto.internal.RatingSubmitCommand;

@Component
public class RatingValidator {
	public static final List<String> CATEGORIES = List.of("Integrity", "Quality", "Friendliness");
	private static final int MAX_IMAGES = 3;
	private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

	public void validate(RatingSubmitCommand command) {
		if (command.bookingRef() == null || command.bookingRef().isBlank()) {
			throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
		}
		validateScore(command.overallScore());
		if (command.reviewComment() != null && command.reviewComment().trim().length() > 1000) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"reviewComment must be 1000 characters or fewer.");
		}
		validateSubcategories(command.subcategoryScores());
		validateEvidence(command.evidence());
	}

	private void validateSubcategories(List<RatingSubmitCommand.SubcategoryScoreCommand> scores) {
		if (scores == null || scores.isEmpty()) return;
		Set<String> names = new HashSet<>();
		for (var score : scores) {
			if (score == null) throw new ApiException(ErrorCode.SUBCATEGORY_SET_INCOMPLETE);
			validateScore(score.score());
			names.add(score.categoryName());
		}
		if (scores.size() != CATEGORIES.size() || !names.equals(Set.copyOf(CATEGORIES))) {
			throw new ApiException(ErrorCode.SUBCATEGORY_SET_INCOMPLETE);
		}
	}

	private void validateScore(Integer score) {
		if (score == null || score < 1 || score > 5) throw new ApiException(ErrorCode.SCORE_OUT_OF_RANGE);
	}

	private void validateEvidence(List<MultipartFile> files) {
		List<MultipartFile> present = presentFiles(files);
		if (present.size() > MAX_IMAGES) throw new ApiException(ErrorCode.TOO_MANY_IMAGES);
		for (MultipartFile file : present) {
			if (file.getSize() > MAX_IMAGE_BYTES) throw new ApiException(ErrorCode.FILE_TOO_LARGE);
			String type = file.getContentType();
			if (type == null || !IMAGE_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
				throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
			}
		}
	}

	public List<MultipartFile> presentFiles(List<MultipartFile> files) {
		return files == null ? List.of() : files.stream().filter(file -> !file.isEmpty()).toList();
	}
}
