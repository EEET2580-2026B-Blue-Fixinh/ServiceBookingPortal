package com.fixinh.sbp.modules.rating.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.storage.ImageStorageService;
import com.fixinh.sbp.modules.booking.dto.internal.BookingRatingView;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
import com.fixinh.sbp.modules.rating.dto.internal.RatingSubmitCommand;
import com.fixinh.sbp.modules.rating.model.Rating;
import com.fixinh.sbp.modules.rating.repository.RatingRepository;

class RatingServiceImplTest {
	private RatingRepository repository;
	private BookingInternalService bookings;
	private SmpBookingClient smp;
	private ImageStorageService images;
	private RatingServiceImpl service;

	@BeforeEach void setUp() {
		repository = mock(RatingRepository.class);
		bookings = mock(BookingInternalService.class);
		smp = mock(SmpBookingClient.class);
		images = mock(ImageStorageService.class);
		service = new RatingServiceImpl(repository, bookings, smp, images, new RatingValidator());
	}

	@Test void submitsCompletedBookingUsingVerifiedSmpSnapshot() {
		BookingRatingView booking = booking(BookingStatus.COMPLETED);
		when(bookings.findOwnedForRating("customer-1", "ORD-2026-1024")).thenReturn(booking);
		when(smp.findBookingForRating(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(images.store(any(), any(), any())).thenReturn(List.of());
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.submit("customer-1", command());

		assertEquals(5, result.overallScore());
		assertEquals("ORD-2026-1024", result.bookingRef());
		verify(repository).save(any(Rating.class));
	}

	@Test void rejectsBookingThatSmpDoesNotReportCompleted() {
		BookingRatingView booking = booking(BookingStatus.WORKING);
		when(bookings.findOwnedForRating("customer-1", "ORD-2026-1024")).thenReturn(booking);
		when(smp.findBookingForRating(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ApiException error = assertThrows(ApiException.class,
				() -> service.submit("customer-1", command()));

		assertEquals(ErrorCode.BOOKING_NOT_COMPLETED, error.getErrorCode());
		verify(images, never()).store(any(), any(), any());
	}

	private BookingRatingView booking(BookingStatus status) {
		return new BookingRatingView("ORD-2026-1024", "customer-1", "SVC-PLUMB-01",
				"Plumbing Repair", status, List.of(new TechnicianRef("TEC-014", "John Tran")), Region.SOUTH);
	}

	private RatingSubmitCommand command() {
		return new RatingSubmitCommand("ORD-2026-1024", 5, "Great service", List.of(
				new RatingSubmitCommand.SubcategoryScoreCommand("Integrity", 5),
				new RatingSubmitCommand.SubcategoryScoreCommand("Quality", 5),
				new RatingSubmitCommand.SubcategoryScoreCommand("Friendliness", 4)), List.of());
	}
}
