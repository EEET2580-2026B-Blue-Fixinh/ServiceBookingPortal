package com.fixinh.sbp.modules.rating.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.bson.types.ObjectId;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpRatingBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpRatingTechnicianDto;
import com.fixinh.sbp.common.storage.ImageStorageService;
import com.fixinh.sbp.common.storage.ImageUploadException;
import com.fixinh.sbp.modules.booking.dto.internal.BookingRatingView;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.rating.dto.external.RatingDetailDto;
import com.fixinh.sbp.modules.rating.dto.external.RatingDto;
import com.fixinh.sbp.modules.rating.dto.internal.RatingSubmitCommand;
import com.fixinh.sbp.modules.rating.interfaces.external.RatingExternalService;
import com.fixinh.sbp.modules.rating.interfaces.internal.RatingInternalService;
import com.fixinh.sbp.modules.rating.model.Rating;
import com.fixinh.sbp.modules.rating.repository.RatingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingExternalService, RatingInternalService {
	private final RatingRepository repository;
	private final BookingInternalService bookingService;
	private final SmpBookingClient smpBookingClient;
	private final ImageStorageService imageStorage;
	private final RatingValidator validator;

	@Override
	public RatingDto submit(String customerId, RatingSubmitCommand command) {
		validator.validate(command);
		BookingRatingView local = bookingService.findOwnedForRating(customerId, command.bookingRef());
		if (repository.existsLiveByBookingRef(command.bookingRef())) throw new ApiException(ErrorCode.RATING_ALREADY_EXISTS);

		SmpRatingBookingDto verified = smpBookingClient.findBookingForRating(toSmp(local));
		if (!customerId.equals(verified.customerId())) throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
		if (!"COMPLETED".equals(verified.status())) throw new ApiException(ErrorCode.BOOKING_NOT_COMPLETED);

		Rating rating = Rating.builder()
				.id(new ObjectId().toHexString())
				.bookingRef(verified.bookingRef()).customerId(customerId)
				.serviceId(verified.serviceId()).serviceName(verified.serviceName())
				.technicians(toTechnicians(verified.technicians()))
				.overallScore(command.overallScore())
				.reviewComment(trimToNull(command.reviewComment()))
				.subcategoryScores(toScores(command.subcategoryScores()))
				.evidenceUrls(List.of()).submittedAt(Instant.now()).homeRegion(local.homeRegion()).build();
		try {
			rating.setEvidenceUrls(imageStorage.store("ratings", rating.getId(), validator.presentFiles(command.evidence())));
		} catch (ImageUploadException ex) {
			throw new ApiException(ErrorCode.UPLOAD_FAILED);
		}
		try {
			return toDto(repository.save(rating));
		} catch (DuplicateKeyException ex) {
			throw new ApiException(ErrorCode.RATING_ALREADY_EXISTS);
		}
	}

	@Override public RatingDetailDto findByBooking(String customerId, String bookingRef) {
		bookingService.findOwnedForRating(customerId, bookingRef);
		return detail(repository.findLiveByBookingRef(bookingRef).orElseThrow(() -> new ApiException(ErrorCode.RATING_NOT_FOUND)));
	}
	@Override public RatingDetailDto findById(String customerId, String ratingId) {
		Rating rating = repository.findLiveById(ratingId).orElseThrow(() -> new ApiException(ErrorCode.RATING_NOT_FOUND));
		if (!customerId.equals(rating.getCustomerId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
		return detail(rating);
	}
	@Override public void delete(String customerId, String ratingId) {
		Rating rating = repository.findLiveById(ratingId).orElseThrow(() -> new ApiException(ErrorCode.RATING_NOT_FOUND));
		if (!customerId.equals(rating.getCustomerId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
		rating.setDeletedAt(Instant.now()); repository.save(rating);
	}
	@Override public boolean hasLiveRating(String bookingRef) { return repository.existsLiveByBookingRef(bookingRef); }
	@Override public List<RatingDto> listMine(String customerId) {
		return repository.findLiveByCustomerId(customerId).stream().map(this::toDto).toList();
	}

	private SmpRatingBookingDto toSmp(BookingRatingView booking) {
		List<SmpRatingTechnicianDto> technicians = booking.technicians() == null ? List.of() : booking.technicians().stream()
				.map(t -> new SmpRatingTechnicianDto(t.technicianId(), t.fullName())).toList();
		return new SmpRatingBookingDto(booking.bookingRef(), booking.customerId(), booking.serviceId(),
				booking.serviceName(), booking.status().name(), technicians);
	}
	private List<Rating.TechnicianSnapshot> toTechnicians(List<SmpRatingTechnicianDto> values) {
		return values == null ? List.of() : values.stream().map(t -> new Rating.TechnicianSnapshot(t.technicianId(), t.fullName())).toList();
	}
	private List<Rating.SubcategoryScore> toScores(List<RatingSubmitCommand.SubcategoryScoreCommand> values) {
		return values == null ? List.of() : values.stream().map(s -> new Rating.SubcategoryScore(s.categoryName(), s.score())).toList();
	}
	private String trimToNull(String value) { if (value == null || value.trim().isEmpty()) return null; return value.trim(); }
	private RatingDto toDto(Rating rating) {
		return new RatingDto(rating.getId(), rating.getBookingRef(), rating.getOverallScore(), rating.getReviewComment(),
				rating.getSubcategoryScores().stream().map(s -> new RatingDto.SubcategoryScoreDto(s.categoryName(), s.score())).toList(),
				rating.getEvidenceUrls(), rating.getSubmittedAt());
	}
	private RatingDetailDto detail(Rating rating) {
		RatingDto dto = toDto(rating);
		return new RatingDetailDto(dto.ratingId(), dto.bookingRef(), dto.overallScore(), dto.reviewComment(),
				dto.subcategoryScores(), dto.evidenceUrls(), dto.submittedAt(), true);
	}
}
