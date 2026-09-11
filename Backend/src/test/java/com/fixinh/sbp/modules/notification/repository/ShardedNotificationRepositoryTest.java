package com.fixinh.sbp.modules.notification.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.mongodb.client.result.UpdateResult;

/**
 * The recipient queries, with both templates mocked.
 *
 * <p>Two things are worth asserting here and nowhere else. <b>Which store was asked</b> is the
 * whole point of sharding and cannot be seen from the service. And <b>what the query contained</b>
 * is where ownership lives — a check written as an {@code if} after the fetch would pass a
 * service-level test just as well while leaking another customer's notification, so the
 * assertion has to be on the query itself.
 */
class ShardedNotificationRepositoryTest {

	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";

	private MongoTemplate south;
	private MongoTemplate north;
	private ShardedNotificationRepository repository;

	@BeforeEach
	void setUp() {
		south = mock(MongoTemplate.class);
		north = mock(MongoTemplate.class);
		repository = new ShardedNotificationRepository(south, north);
	}

	// --- Sharding: only the recipient's own store is touched --------------------------------

	@Test
	void readsASouthernRecipientFromTheSouthernStoreOnly() {
		when(south.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

		repository.findForRecipient(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				false, 0, 20);

		verify(south).find(any(Query.class), eq(Notification.class));
		verifyNoInteractions(north);
	}

	@Test
	void readsANorthernRecipientFromTheNorthernStoreOnly() {
		when(north.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

		repository.findForRecipient(Region.NORTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				false, 0, 20);

		verify(north).find(any(Query.class), eq(Notification.class));
		verifyNoInteractions(south);
	}

	// --- §9.1: what the list query actually asks for ----------------------------------------

	@Test
	void scopesEveryListToOneRecipientAndSortsNewestFirst() {
		when(south.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

		repository.findForRecipient(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				false, 2, 20);

		String query = captureFind().toString();
		assertTrue(query.contains(CUSTOMER_ID), "the recipient must be part of the query");
		assertTrue(query.contains("recipientType"),
				"matched on kind as well as identifier, so a staff row can never surface here");
		assertFalse(query.contains("readStatus"),
				"without unreadOnly the list is everything, read and unread alike");
	}

	@Test
	void restrictsToUnreadOnlyWhenAsked() {
		when(south.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

		repository.findForRecipient(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				true, 0, 20);

		assertTrue(captureFind().toString().contains("readStatus"));
	}

	@Test
	void pagesBySkippingWholePages() {
		when(south.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

		repository.findForRecipient(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				false, 3, 20);

		Query query = captureFind();
		assertEquals(60L, query.getSkip(), "page 3 of 20 starts at 60, not at 3");
		assertEquals(20, query.getLimit());
	}

	// --- §9.2: ownership is the query, and idempotency is matchedCount ----------------------

	@Test
	void marksReadOnlyWhereTheRecipientMatchesToo() {
		when(south.updateFirst(any(Query.class), any(UpdateDefinition.class),
				eq(Notification.class))).thenReturn(updateResult(1L, 1L));

		repository.markRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID, "NOTIF-1");

		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(south).updateFirst(captor.capture(), any(UpdateDefinition.class),
				eq(Notification.class));

		String query = captor.getValue().toString();
		assertTrue(query.contains("NOTIF-1"), "the notification asked for");
		assertTrue(query.contains(CUSTOMER_ID),
				"and the caller -- without this, one customer could mark another's read");
	}

	@Test
	void treatsAnAlreadyReadNotificationAsSuccess() {
		// Matched but not modified: it was already read. §9.2 requires a second call to
		// succeed, so matchedCount is the number that answers "is this yours".
		when(south.updateFirst(any(Query.class), any(UpdateDefinition.class),
				eq(Notification.class))).thenReturn(updateResult(1L, 0L));

		assertTrue(repository.markRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				"NOTIF-1"), "modifiedCount would be 0 here and would wrongly produce a 404");
	}

	@Test
	void reportsNothingMatchedForAnotherRecipientsNotification() {
		when(south.updateFirst(any(Query.class), any(UpdateDefinition.class),
				eq(Notification.class))).thenReturn(updateResult(0L, 0L));

		assertFalse(repository.markRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				"SOMEONE-ELSES"));
	}

	@Test
	void breaksTiesOnIdSoPagingCannotRepeatOrSkip() {
		when(south.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

		repository.findForRecipient(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID,
				false, 0, 20);

		String sort = captureFind().getSortObject().toString();
		assertTrue(sort.contains("createdAt"), "newest first");
		assertTrue(sort.contains("_id"),
				"and a unique second key -- millisecond timestamps tie, and tied documents "
						+ "drift between pages without one");
	}

	@Test
	void marksAllUnreadInASingleWrite() {
		when(south.updateMulti(any(Query.class), any(UpdateDefinition.class),
				eq(Notification.class))).thenReturn(updateResult(5L, 5L));

		assertEquals(5L, repository.markAllRead(Region.SOUTH, RecipientType.CUSTOMER,
				CUSTOMER_ID));

		ArgumentCaptor<UpdateDefinition> captor =
				ArgumentCaptor.forClass(UpdateDefinition.class);
		verify(south).updateMulti(any(Query.class), captor.capture(), eq(Notification.class));
		assertTrue(captor.getValue().toString().contains("readStatus"));
	}

	// --- The retry sweep: one store down must not hide the other's backlog -----------------

	@Test
	void findsOutstandingEmailsInBothStoresSouthFirst() {
		Notification inSouth = outstanding("SOUTH-1");
		Notification inNorth = outstanding("NORTH-1");
		when(south.find(any(Query.class), eq(Notification.class))).thenReturn(List.of(inSouth));
		when(north.find(any(Query.class), eq(Notification.class))).thenReturn(List.of(inNorth));

		assertEquals(List.of(inSouth, inNorth), repository.findOutstandingEmails(50));
	}

	@Test
	void stillReturnsOneStoresBacklogWhenTheOtherIsDown() {
		// A regional outage should delay that region's welcome emails and nobody else's. One
		// combined query let an unreachable south store stop every email owed in the north.
		Notification inNorth = outstanding("NORTH-1");
		when(south.find(any(Query.class), eq(Notification.class)))
				.thenThrow(new DataAccessResourceFailureException("south unreachable"));
		when(north.find(any(Query.class), eq(Notification.class))).thenReturn(List.of(inNorth));

		assertEquals(List.of(inNorth), repository.findOutstandingEmails(50));
	}

	@Test
	void failsLoudlyWhenNeitherStoreAnswers() {
		// An empty list would read as "nothing is owed" -- the one answer certain to be wrong
		// when nothing could be read at all.
		when(south.find(any(Query.class), eq(Notification.class)))
				.thenThrow(new DataAccessResourceFailureException("south unreachable"));
		when(north.find(any(Query.class), eq(Notification.class)))
				.thenThrow(new DataAccessResourceFailureException("north unreachable"));

		assertThrows(IllegalStateException.class, () -> repository.findOutstandingEmails(50));
	}

	// --- Refusing rather than defaulting ----------------------------------------------------

	@Test
	void refusesToGuessAStoreWhenNoRegionWasResolved() {
		// Falling through to a default store would return an empty list, which reads exactly
		// like a recipient who has no notifications. A silent wrong answer is worse than none.
		assertThrows(IllegalStateException.class, () -> repository.findForRecipient(
				null, RecipientType.CUSTOMER, CUSTOMER_ID, false, 0, 20));

		verifyNoInteractions(south, north);
	}

	@Test
	void refusesABlankRecipient() {
		assertThrows(IllegalArgumentException.class, () -> repository.count(
				Region.SOUTH, RecipientType.CUSTOMER, "  ", false));

		verifyNoInteractions(south, north);
	}

	@Test
	void refusesToSaveANotificationWithNoHomeRegion() {
		assertThrows(IllegalStateException.class, () -> repository.save(new Notification()));

		verifyNoInteractions(south, north);
	}

	/** A row as the outstanding-email query would return it; only its identity matters here. */
	private Notification outstanding(String id) {
		Notification notification = new Notification();
		notification.setId(id);
		return notification;
	}

	private Query captureFind() {
		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(south).find(captor.capture(), eq(Notification.class));
		return captor.getValue();
	}

	private UpdateResult updateResult(long matched, long modified) {
		// A real result rather than a mock. Building a mock inside when(...).thenReturn(...)
		// opens a second stubbing while the first is still in progress, and Mockito keeps that
		// state globally -- which is what UnfinishedStubbingException reports.
		return UpdateResult.acknowledged(matched, modified, null);
	}
}