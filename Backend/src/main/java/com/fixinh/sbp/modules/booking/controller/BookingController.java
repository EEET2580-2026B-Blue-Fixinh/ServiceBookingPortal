package com.fixinh.sbp.modules.booking.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.booking.dto.external.BookingListResponse;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;
import com.fixinh.sbp.modules.booking.dto.internal.BookingSearchFilter;
import com.fixinh.sbp.modules.booking.interfaces.external.BookingExternalService;

import lombok.RequiredArgsConstructor;

/** Base path /bookings (API Documentation Section 7). Customer-only — see SecurityConfig. */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
	private final BookingExternalService bookingService;
	/**
	 * Section 7.1. Creates a service booking for exactly one repair service (Req 3.3.S1–S3).
	 * The owner is always the signed-in customer — never an id the client could supply.
	 */
	@PostMapping
	public ResponseEntity<BookingSummaryDto> createBooking(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestBody BookingCreateCommand command) {
		BookingSummaryDto booking = bookingService.createBooking(principal.id(), command);
		return ResponseEntity
				.created(URI.create("/api/v1/bookings/" + booking.bookingRef()))
				.body(booking);
	}

	/**
	 * Section 7.2. The signed-in customer's own bookings, grouped, searched, filtered and sorted
	 * server-side (Reqs 3.3.S4, 3.3.M2–M6). {@code serviceId} is repeatable — Spring binds every
	 * occurrence of the query parameter into the list.
	 */
	@GetMapping
	public BookingListResponse listBookings(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) List<String> serviceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
			@RequestParam(required = false) String group,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		BookingSearchFilter filter = new BookingSearchFilter(
				q, serviceId, createdFrom, createdTo, group, sort, page, size);
		return bookingService.listBookings(principal.id(), filter);
	}
}
