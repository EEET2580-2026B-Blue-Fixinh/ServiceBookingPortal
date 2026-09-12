package com.fixinh.sbp.modules.integration.service;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto;

/**
 * Checks an inbound booking event against §11.7's rules before anything acts on it.
 *
 * <p>Separate from the service for the same reason the booking module's validators are: what
 * makes a request acceptable is a rule of its own, and reading it should not mean reading the
 * work it guards.
 *
 * <p>Every failure is {@code 400 EVENT_MALFORMED} with a message naming the field, because
 * §11.7 tells SMP not to retry this class of error and a message that does not say what to
 * fix makes that instruction impossible to follow.
 */
@Component
public class BookingEventValidator {

	/**
	 * @throws ApiException 400 {@code EVENT_MALFORMED} on the first rule broken
	 */
	public void validate(BookingStatusWebhookDto event) {
		if (event == null) {
			throw malformed("The request body is empty.");
		}

		requireText(event.eventId(), "eventId",
				"SBP recognises a replay by it, so an event without one could not be retried "
						+ "safely.");
		requireText(event.eventType(), "eventType", null);
		requireText(event.bookingRef(), "bookingRef", null);
		requireText(event.customerId(), "customerId",
				"It is what routes the event to a regional store and a recipient.");

		if (event.occurredAt() == null) {
			throw malformed("occurredAt is required. SBP records it as the moment the "
					+ "projection became current, and uses it to recognise an event that "
					+ "arrived out of order.");
		}
		if (event.payload() == null) {
			throw malformed("payload is required: it carries the changed booking.");
		}

		// The status is the one payload field always present in §11.7's examples, and the one
		// thing SBP acts on -- there is no refresh to perform without it.
		requireText(event.payload().status(), "payload.status", null);
		requireKnownStatus(event.payload().status());
		requireCompleteBill(event.payload());
	}

	/**
	 * §11.7's status must be one of Appendix A's eight.
	 *
	 * <p>Rejected rather than ignored. A status SBP does not know is not a status it can
	 * silently keep the old value for: the booking would stay in a state SMP believes it has
	 * left, and every screen would show something SMP no longer thinks is true. A 400 tells
	 * SMT the vocabularies have diverged, which is a thing worth finding out.
	 */
	private static void requireKnownStatus(String status) {
		try {
			BookingStatus.valueOf(status);
		}
		catch (IllegalArgumentException unknown) {
			throw malformed("payload.status is '" + status + "', which SBP does not recognise. "
					+ "Expected one of CREATED, CONFIRMED, SCHEDULED, WORKING, COMPLETED, "
					+ "CANCELLED_BY_CUSTOMER, TERMINATED_BY_CUSTOMER, ABORTED_BY_TECHNICIAN.");
		}
	}

	/**
	 * A bill needs both of the numbers its total is made of (Req 3.3.S6).
	 *
	 * <p>Absent entirely is fine — most events carry no bill. But half a bill cannot be
	 * stored: SBP computes the total from the two components, and treating a missing one as
	 * zero would publish a total the customer could tell was wrong.
	 */
	private static void requireCompleteBill(BookingStatusWebhookDto.BookingPayload payload) {
		boolean anyBillField = payload.serviceFee() != null || payload.materialCost() != null
				|| payload.totalCost() != null || payload.billImageUrl() != null
				|| payload.billIssuedAt() != null;

		if (!anyBillField) {
			return;
		}
		if (payload.serviceFee() == null || payload.materialCost() == null) {
			throw malformed("A bill needs both payload.serviceFee and payload.materialCost. "
					+ "SBP computes the total from the two rather than storing the one you send, "
					+ "so it cannot accept a bill with either missing.");
		}
		if (payload.serviceFee() < 0 || payload.materialCost() < 0) {
			throw malformed("payload.serviceFee and payload.materialCost cannot be negative.");
		}
	}

	private static void requireText(String value, String field, String why) {
		if (value == null || value.isBlank()) {
			throw malformed(field + " is required." + (why == null ? "" : " " + why));
		}
	}

	private static ApiException malformed(String message) {
		return new ApiException(ErrorCode.EVENT_MALFORMED, message);
	}
}
