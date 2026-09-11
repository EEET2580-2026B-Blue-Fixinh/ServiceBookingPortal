package com.fixinh.sbp.modules.integration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto.BookingPayload;

/**
 * §11.7's rules. Each refusal is checked for its message as well as its code, because §11.7
 * tells SMP not to retry a malformed event — advice it can only follow if the response says
 * which field to fix.
 */
class BookingEventValidatorTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T03:19:58Z");

	private final BookingEventValidator validator = new BookingEventValidator();

	@Test
	void acceptsSection117sOwnExample() {
		assertDoesNotThrow(() -> validator.validate(new BookingStatusWebhookDto(
				"evt-8f2c14a9", "BOOKING_STATUS_CHANGED", OCCURRED_AT, "ORD-2026-1024",
				"66f1a2b3c4d5e6f708192a3b",
				new BookingPayload("SCHEDULED", Instant.parse("2026-08-24T18:00:00Z"), 120,
						List.of(new TechnicianRef("TEC-014", "John Tran")),
						null, null, null, null, null))));
	}

	@Test
	void acceptsAnEventTypeItHasNoSpecialHandlingFor() {
		// SBP reacts to the payload's status, not to the event type, so a type outside §11.7's
		// four is still a legitimate projection refresh. Recording what SMP said beats
		// refusing a change SBP could have applied.
		assertDoesNotThrow(() -> validator.validate(event(
				new BookingPayload("WORKING", null, null, null, null, null, null, null, null),
				"SOMETHING_NEW")));
	}

	@Test
	void refusesAnEmptyBody() {
		assertEquals(ErrorCode.EVENT_MALFORMED,
				assertThrows(ApiException.class, () -> validator.validate(null)).getErrorCode());
	}

	@Test
	void namesTheMissingRequiredField() {
		assertMalformedMentioning("eventId", new BookingStatusWebhookDto(
				null, "BOOKING_STATUS_CHANGED", OCCURRED_AT, "ORD-1", "CUST-1", confirmed()));
		assertMalformedMentioning("eventType", new BookingStatusWebhookDto(
				"evt-1", "  ", OCCURRED_AT, "ORD-1", "CUST-1", confirmed()));
		assertMalformedMentioning("bookingRef", new BookingStatusWebhookDto(
				"evt-1", "BOOKING_STATUS_CHANGED", OCCURRED_AT, null, "CUST-1", confirmed()));
		assertMalformedMentioning("customerId", new BookingStatusWebhookDto(
				"evt-1", "BOOKING_STATUS_CHANGED", OCCURRED_AT, "ORD-1", "", confirmed()));
		assertMalformedMentioning("occurredAt", new BookingStatusWebhookDto(
				"evt-1", "BOOKING_STATUS_CHANGED", null, "ORD-1", "CUST-1", confirmed()));
		assertMalformedMentioning("payload", new BookingStatusWebhookDto(
				"evt-1", "BOOKING_STATUS_CHANGED", OCCURRED_AT, "ORD-1", "CUST-1", null));
		assertMalformedMentioning("payload.status", event(
				new BookingPayload(null, null, null, null, null, null, null, null, null)));
	}

	@Test
	void refusesAStatusOutsideAppendixA() {
		// Keeping the old status would leave the booking in a state SMP believes it has left,
		// on every screen, with nothing reporting it. A 400 says the vocabularies diverged.
		ApiException refused = assertThrows(ApiException.class, () -> validator.validate(event(
				new BookingPayload("PENDING", null, null, null, null, null, null, null, null))));

		assertEquals(ErrorCode.EVENT_MALFORMED, refused.getErrorCode());
		assertTrue(refused.getMessage().contains("PENDING"), "says what it received");
		assertTrue(refused.getMessage().contains("CONFIRMED"), "and what it expected");
	}

	@Test
	void acceptsEveryStatusAppendixADefines() {
		for (BookingStatus status : BookingStatus.values()) {
			assertDoesNotThrow(() -> validator.validate(event(new BookingPayload(
					status.name(), null, null, null, null, null, null, null, null))));
		}
	}

	// --- The bill ---------------------------------------------------------------------------

	@Test
	void acceptsAnEventWithNoBillAtAll() {
		assertDoesNotThrow(() -> validator.validate(event(confirmed())));
	}

	@Test
	void acceptsACompleteBill() {
		assertDoesNotThrow(() -> validator.validate(event(new BookingPayload("COMPLETED",
				null, null, null, 300_000L, 150_000L, 450_000L,
				"https://res.cloudinary.com/bill.jpg", OCCURRED_AT))));
	}

	@Test
	void refusesHalfABill() {
		// SBP computes the total from both components; a missing one taken as zero would
		// publish a total the customer can see is wrong.
		ApiException refused = assertThrows(ApiException.class, () -> validator.validate(event(
				new BookingPayload("COMPLETED", null, null, null, 300_000L, null, 300_000L,
						null, null))));

		assertEquals(ErrorCode.EVENT_MALFORMED, refused.getErrorCode());
		assertTrue(refused.getMessage().contains("materialCost"));
	}

	@Test
	void refusesABillKnownOnlyByItsTotal() {
		// A total with no parts is the one number SBP will not store: §12.3 asks it to
		// recompute, and there is nothing here to recompute from.
		assertMalformedMentioning("serviceFee", event(new BookingPayload("COMPLETED",
				null, null, null, null, null, 450_000L, null, null)));
	}

	@Test
	void refusesNegativeMoney() {
		assertMalformedMentioning("negative", event(new BookingPayload("COMPLETED",
				null, null, null, -1L, 150_000L, null, null, null)));
	}

	private void assertMalformedMentioning(String fragment, BookingStatusWebhookDto event) {
		ApiException refused = assertThrows(ApiException.class, () -> validator.validate(event));

		assertEquals(ErrorCode.EVENT_MALFORMED, refused.getErrorCode());
		assertTrue(refused.getMessage().contains(fragment),
				"the message should name " + fragment + " but was: " + refused.getMessage());
	}

	private static BookingPayload confirmed() {
		return new BookingPayload("CONFIRMED", null, null, null, null, null, null, null, null);
	}

	private static BookingStatusWebhookDto event(BookingPayload payload) {
		return event(payload, "BOOKING_STATUS_CHANGED");
	}

	private static BookingStatusWebhookDto event(BookingPayload payload, String eventType) {
		return new BookingStatusWebhookDto("evt-8f2c14a9", eventType, OCCURRED_AT,
				"ORD-2026-1024", "66f1a2b3c4d5e6f708192a3b", payload);
	}
}
