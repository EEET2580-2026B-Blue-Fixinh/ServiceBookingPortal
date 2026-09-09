package com.fixinh.sbp.modules.rating.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.common.dto.ListResponse;
import com.fixinh.sbp.modules.rating.dto.external.RatingDetailDto;
import com.fixinh.sbp.modules.rating.dto.external.RatingDto;
import com.fixinh.sbp.modules.rating.dto.internal.RatingSubmitCommand;
import com.fixinh.sbp.modules.rating.interfaces.external.RatingExternalService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RatingController {
	private final RatingExternalService service;
	private final ObjectMapper objectMapper;

	@PostMapping(path = "/ratings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RatingDto> submit(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam String bookingRef, @RequestParam Integer overallScore,
			@RequestParam(required = false) String reviewComment,
			@RequestParam(required = false) String subcategoryScores,
			@RequestPart(required = false) List<MultipartFile> evidence) {
		RatingDto rating = service.submit(principal.id(), new RatingSubmitCommand(bookingRef,
				overallScore, reviewComment, parseScores(subcategoryScores), evidence));
		return ResponseEntity.created(URI.create("/api/v1/ratings/" + rating.ratingId())).body(rating);
	}

	@GetMapping("/bookings/{bookingRef}/rating")
	public RatingDetailDto findByBooking(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable String bookingRef) { return service.findByBooking(principal.id(), bookingRef); }

	@GetMapping("/ratings/{ratingId}")
	public RatingDetailDto findById(@AuthenticationPrincipal UserPrincipal principal,
				@PathVariable String ratingId) { return service.findById(principal.id(), ratingId); }

	@GetMapping("/ratings")
	public ListResponse<RatingDto> listMine(@AuthenticationPrincipal UserPrincipal principal) {
		return new ListResponse<>(service.listMine(principal.id()));
	}

	@DeleteMapping("/ratings/{ratingId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable String ratingId) {
		service.delete(principal.id(), ratingId);
		return ResponseEntity.noContent().build();
	}

	private List<RatingSubmitCommand.SubcategoryScoreCommand> parseScores(String json) {
		if (json == null || json.isBlank()) return List.of();
		try {
			return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(
					List.class, RatingSubmitCommand.SubcategoryScoreCommand.class));
		} catch (Exception ex) {
			throw new ApiException(ErrorCode.SUBCATEGORY_SET_INCOMPLETE,
					"subcategoryScores must be a JSON array containing every rating category.");
		}
	}
}
