package com.fixinh.sbp.modules.notification.dto.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;

/**
 * The two factories, and what each says about the event it came from. The checks the
 * canonical constructor runs are exercised through the service; this is only about the id.
 */
class NotificationCreateCommandTest {

	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";

	@Test
	void aCommandFromAnEventCarriesThatEventsId() {
		NotificationCreateCommand command = NotificationCreateCommand.forBookingEvent(
				"evt-8f2c14a9", CUSTOMER_ID, NotificationType.BOOKING_CONFIRMED,
				"Your order ORD-2026-1024 has been confirmed.", "ORD-2026-1024");

		assertEquals("evt-8f2c14a9", command.sourceEventId());
		assertEquals(RecipientType.CUSTOMER, command.recipientType());
		assertEquals(CUSTOMER_ID, command.recipientIdentifier());
		assertNull(command.provinceCode(), "a customer is located by their profile, not a code");
	}

	@Test
	void aCommandFromNoEventCarriesNoId() {
		NotificationCreateCommand command = NotificationCreateCommand.forCustomerBooking(
				CUSTOMER_ID, NotificationType.BOOKING_CONFIRMED, "Confirmed.", "ORD-1");

		assertNull(command.sourceEventId(),
				"nothing to recognise a repeat by, so every call is a new notification");
	}

	@Test
	void theSixArgumentConstructorMeansNoEvent() {
		NotificationCreateCommand command = new NotificationCreateCommand(
				RecipientType.STAFF, "STF-001", "HCM", NotificationType.STAFF_ACCOUNT_CREATED,
				"Welcome email sent to tuan@fixinh.vn", null);

		assertNull(command.sourceEventId());
	}

	@Test
	void refusesAnEventCommandThatCannotNameItsEvent() {
		// Filed as new on every replay, which is the outcome the factory exists to prevent.
		for (String eventId : new String[] {null, "", "   "}) {
			assertThrows(IllegalArgumentException.class,
					() -> NotificationCreateCommand.forBookingEvent(eventId, CUSTOMER_ID,
							NotificationType.BOOKING_CONFIRMED, "Confirmed.", "ORD-1"));
		}
	}
}
