package com.fixinh.sbp.modules.integration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.sharding.Region;

/**
 * Seen versus done, as plain state. The distinction is what decides whether a replay is
 * answered or re-run, so it is pinned here rather than left to whoever reads {@code handledAt}.
 */
class IntegrationEventTest {

	private static final Instant RECEIVED = Instant.parse("2026-08-11T03:20:00Z");

	@Test
	void aFreshlyReceivedEventIsSeenButNotHandled() {
		IntegrationEvent event = received();

		assertFalse(event.isHandled(),
				"accepted is not finished -- a replay of this one must run the listeners again");
		assertNull(event.getHandledAt());
	}

	@Test
	void markingHandledRecordsWhen() {
		IntegrationEvent event = received();
		Instant done = RECEIVED.plusSeconds(1);

		event.markHandled(done);

		assertTrue(event.isHandled());
		assertEquals(done, event.getHandledAt(),
				"the TTL index counts from this, so it has to be the real moment");
	}

	@Test
	void keepsTheEventIdAsItsOwn() {
		// The id is Mongo's _id, which is what makes a second insert of the same event a
		// database refusal rather than a check that could race.
		assertEquals("evt-8f2c14a9", received().getEventId());
	}

	private static IntegrationEvent received() {
		return IntegrationEvent.builder()
				.eventId("evt-8f2c14a9")
				.direction(EventDirection.IN)
				.eventType("BOOKING_STATUS_CHANGED")
				.bookingRef("ORD-2026-1024")
				.customerId("66f1a2b3c4d5e6f708192a3b")
				.occurredAt(RECEIVED.minusSeconds(2))
				.receivedAt(RECEIVED)
				.homeRegion(Region.SOUTH)
				.build();
	}
}
