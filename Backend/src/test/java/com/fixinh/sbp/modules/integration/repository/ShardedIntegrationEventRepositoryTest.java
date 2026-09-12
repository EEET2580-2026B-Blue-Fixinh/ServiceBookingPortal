package com.fixinh.sbp.modules.integration.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.integration.model.EventDirection;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;

/**
 * Which store, and which operation. The second matters as much as the first here: an
 * {@code insert} that had quietly become a {@code save} would overwrite the very row that
 * says an event was already handled.
 */
class ShardedIntegrationEventRepositoryTest {

	private static final String EVENT_ID = "evt-8f2c14a9";

	private MongoTemplate south;
	private MongoTemplate north;
	private ShardedIntegrationEventRepository repository;

	@BeforeEach
	void setUp() {
		south = mock(MongoTemplate.class);
		north = mock(MongoTemplate.class);
		repository = new ShardedIntegrationEventRepository(south, north);
	}

	@Test
	void insertsANewEventIntoTheCustomersStoreOnly() {
		IntegrationEvent event = inbound(Region.NORTH);
		when(north.insert(event)).thenReturn(event);

		repository.insert(event);

		verify(north).insert(event);
		verify(north, never()).save(any());
		verifyNoInteractions(south);
	}

	@Test
	void insertsRatherThanSavesSoASecondInsertIsRefused() {
		// save() on an existing _id is an upsert, and an upsert of a handled event's row would
		// erase the handledAt that makes the next replay a duplicate. insert() is refused by
		// the store instead, which is the answer the service wants.
		IntegrationEvent event = inbound(Region.SOUTH);
		when(south.insert(event)).thenReturn(event);

		repository.insert(event);

		verify(south).insert(event);
		verify(south, never()).save(any(IntegrationEvent.class));
	}

	@Test
	void savesAHandledEventBackToTheSameStore() {
		IntegrationEvent event = inbound(Region.SOUTH);
		event.markHandled(Instant.parse("2026-08-11T03:20:01Z"));
		when(south.save(event)).thenReturn(event);

		repository.save(event);

		verify(south).save(event);
		verifyNoInteractions(north);
	}

	@Test
	void looksAnEventUpInOneStoreByIdAndDirection() {
		IntegrationEvent filed = inbound(Region.SOUTH);
		when(south.findOne(any(Query.class), eq(IntegrationEvent.class))).thenReturn(filed);

		assertSame(filed, repository.findInbound(Region.SOUTH, EVENT_ID).orElseThrow());

		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(south).findOne(captor.capture(), eq(IntegrationEvent.class));
		verifyNoInteractions(north);

		String query = captor.getValue().toString();
		assertTrue(query.contains(EVENT_ID), "the event asked for");
		assertTrue(query.contains("direction"),
				"and only the inbound one -- the collection is shared with the outbox by design");
	}

	@Test
	void answersEmptyOnAFirstDelivery() {
		when(south.findOne(any(Query.class), eq(IntegrationEvent.class))).thenReturn(null);

		assertTrue(repository.findInbound(Region.SOUTH, EVENT_ID).isEmpty());
	}

	@Test
	void answersEmptyForABlankIdWithoutAskingAStore() {
		assertTrue(repository.findInbound(Region.SOUTH, "  ").isEmpty());
		assertTrue(repository.findInbound(Region.SOUTH, null).isEmpty());

		verifyNoInteractions(south, north);
	}

	@Test
	void refusesToLookUpOrWriteInAGuessedStore() {
		// Empty from the wrong store reads like a first delivery; a row in the wrong store is
		// one no replay will ever find. Both are the silent failure the refusal prevents.
		assertThrows(IllegalStateException.class,
				() -> repository.findInbound(null, EVENT_ID));
		assertThrows(IllegalStateException.class,
				() -> repository.insert(inbound(null)));
		assertThrows(IllegalStateException.class,
				() -> repository.save(inbound(null)));

		verifyNoInteractions(south, north);
	}

	private static IntegrationEvent inbound(Region region) {
		IntegrationEvent event = IntegrationEvent.builder()
				.eventId(EVENT_ID)
				.direction(EventDirection.IN)
				.eventType("BOOKING_STATUS_CHANGED")
				.bookingRef("ORD-2026-1024")
				.customerId("66f1a2b3c4d5e6f708192a3b")
				.occurredAt(Instant.parse("2026-08-11T03:19:58Z"))
				.receivedAt(Instant.parse("2026-08-11T03:20:00Z"))
				.homeRegion(region)
				.build();
		assertEquals(EVENT_ID, event.getEventId());
		return event;
	}
}
