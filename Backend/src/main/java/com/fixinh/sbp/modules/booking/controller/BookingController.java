package com.fixinh.sbp.modules.booking.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;
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
}
