package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.notification.dto.external.NotificationDto;
import com.fixinh.sbp.modules.notification.dto.external.NotificationListDto;
import com.fixinh.sbp.modules.notification.dto.external.NotificationPushDto;
import com.fixinh.sbp.modules.notification.dto.internal.NotificationCreateCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;
import com.fixinh.sbp.modules.notification.websocket.NotificationGateway;

/**
 * The send path, with the provider and the database mocked and everything else real.
 *
 * <p>The repository mock keeps every version it is handed, so the assertions can check the
 * order of writes as well as the final state — which is the whole point of saving before the
 * provider is called.
 */
class NotificationServiceImplTest {

	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final String PASSWORD = "Tmp!Passw0rd";

	private static final StaffWelcomeEmailCommand COMMAND = new StaffWelcomeEmailCommand(
			"STF-001", "Nguyen Minh Tuan", "tuan@fixinh.vn", "TECHNICIAN", PASSWORD, "HCM");

	private final List<Notification> saves = new ArrayList<>();

	private EmailClient emailClient;
	private ShardDirectoryRepository shardDirectory;
	private NotificationRepository repository;
	private NotificationGateway gateway;
	private NotificationServiceImpl service;

	@BeforeEach
	void setUp() {
		saves.clear();
		emailClient = mock(EmailClient.class);
		shardDirectory = mock(ShardDirectoryRepository.class);

		when(shardDirectory.findRegion(SubjectType.STAFF, "STF-001")).thenReturn(Optional.empty());
		when(shardDirectory.register(SubjectType.STAFF, "STF-001", Region.SOUTH))
				.thenReturn(Region.SOUTH);

		// The customer's store is already known, so regionForCustomer takes the directory hit
		// and never reaches for a profile. Keeps the read-path tests about the service.
		when(shardDirectory.findRegion(SubjectType.CUSTOMER, CUSTOMER_ID))
				.thenReturn(Optional.of(Region.SOUTH));

		repository = mock(NotificationRepository.class);
		gateway = mock(NotificationGateway.class);
		when(repository.save(any())).thenAnswer(invocation -> {
			Notification given = invocation.getArgument(0);
			if (given.getId() == null) {
				given.setId("NOTIF-1");
			}
			saves.add(copyOf(given));
			return given;
		});

		SbpProperties properties = new SbpProperties();
		properties.getEmail().setFromAddress("no-reply@fixinh.vn");
		properties.getEmail().setFromName("Fixinh");
		properties.getEmail().setStaffSignInUrl("https://smp.fixinh.vn/login");

		service = new NotificationServiceImpl(
				new StaffWelcomeEmailComposer(properties),
				// A staff welcome email routes by branch province, never by a customer profile,
				// so the customer service is a bare mock here -- unused by this path on purpose.
				new NotificationRegionResolver(
						shardDirectory, new RegionResolver(), mock(CustomerExternalService.class)),
				repository,
				emailClient,
				gateway);
	}

	@Test
	void sendsOneEmailAndReportsItWentOut() {
		StaffWelcomeEmailResult result = service.sendStaffWelcomeEmail(COMMAND);

		assertTrue(result.sent(), "this is what §10.6 puts in notificationSent");
		assertNotNull(result.notificationId());
		verify(emailClient).send(any(EmailMessage.class));
	}

	@Test
	void writesTheRecordBeforeCallingTheProvider() {
		service.sendStaffWelcomeEmail(COMMAND);

		assertEquals(2, saves.size(), "one write before the attempt, one after");
		assertEquals(DeliveryStatus.PENDING, saves.get(0).getDeliveryStatus(),
				"the first write must land before the provider is called, so a crash mid-send "
						+ "still leaves the sweeper something to find");
		assertEquals(0, saves.get(0).getAttemptCount());
		assertEquals(DeliveryStatus.SENT, saves.get(1).getDeliveryStatus());
	}

	@Test
	void buildsTheRecordTheCdmAndTheIssueAskFor() {
		service.sendStaffWelcomeEmail(COMMAND);

		Notification recorded = saves.get(1);
		assertEquals(RecipientType.STAFF, recorded.getRecipientType());
		assertEquals(NotificationType.STAFF_ACCOUNT_CREATED, recorded.getType());
		assertEquals(DeliveryChannel.EMAIL, recorded.getDeliveryChannel());
		assertEquals("STF-001", recorded.getRecipientIdentifier());
		assertEquals("tuan@fixinh.vn", recorded.getRecipientAddress());
		assertEquals(Region.SOUTH, recorded.getHomeRegion(), "HCM is a southern branch");
		assertEquals(1, recorded.getAttemptCount());
		assertNotNull(recorded.getSentAt());
	}

	@Test
	void theStoredSummaryNeverCarriesThePassword() {
		service.sendStaffWelcomeEmail(COMMAND);

		for (Notification version : saves) {
			assertFalse(version.getMessage().contains(PASSWORD),
					"the notification's message outlives delivery and must not hold a credential");
		}
		assertNull(saves.get(1).getUndeliveredBody(),
				"a successful send discards the body that carried the password");
	}

	@Test
	void aFailedSendIsReportedRatherThanThrown() {
		doThrow(new EmailDeliveryException("provider refused"))
				.when(emailClient).send(any(EmailMessage.class));

		StaffWelcomeEmailResult result = service.sendStaffWelcomeEmail(COMMAND);

		assertFalse(result.sent(), "§10.6 turns this into 502 EMAIL_DELIVERY_FAILED");
		assertNotNull(result.notificationId(), "the attempt is still on record");
	}

	@Test
	void aFailedSendRecordsAnAttemptAndKeepsTheBodyForRetry() {
		doThrow(new EmailDeliveryException("provider refused"))
				.when(emailClient).send(any(EmailMessage.class));

		service.sendStaffWelcomeEmail(COMMAND);

		Notification recorded = saves.get(saves.size() - 1);
		assertEquals(DeliveryStatus.FAILED, recorded.getDeliveryStatus());
		assertEquals(1, recorded.getAttemptCount(), "the sweeper counts against this");
		assertNull(recorded.getSentAt(), "nothing was sent");
		assertTrue(recorded.isAwaitingDelivery());
		assertTrue(recorded.getUndeliveredBody().contains(PASSWORD),
				"the retry has to resend the same letter, so the letter survives the failure");
	}

	@Test
	void registersTheStaffMemberInTheShardDirectory() {
		service.sendStaffWelcomeEmail(COMMAND);

		// The point of registering: the next message to this staff member needs no province.
		verify(shardDirectory).register(SubjectType.STAFF, "STF-001", Region.SOUTH);
	}

	@Test
	void anUnroutableBranchStopsBeforeAnythingIsWrittenOrSent() {
		StaffWelcomeEmailCommand nowhere = new StaffWelcomeEmailCommand(
				"STF-002", "Someone", "someone@fixinh.vn", "RECEPTIONIST", PASSWORD, "ATLANTIS");
		when(shardDirectory.findRegion(SubjectType.STAFF, "STF-002")).thenReturn(Optional.empty());

		ApiException thrown = assertThrows(ApiException.class,
				() -> service.sendStaffWelcomeEmail(nowhere));

		assertSame(ErrorCode.PROVINCE_CODE_INVALID, thrown.getErrorCode());
		assertTrue(saves.isEmpty(), "nothing filed in a store we could not identify");
		verify(emailClient, never()).send(any(EmailMessage.class));
	}

	@Test
	void theCommandRedactsThePasswordWhenPrinted() {
		// The acceptance criterion is about logs, and this is the object most likely to end up
		// in one.
		assertFalse(COMMAND.toString().contains(PASSWORD),
				"toString must not expose the initial password");
		assertTrue(COMMAND.toString().contains("STF-001"), "but must stay useful for debugging");
	}

	// --- §9.1: the list -------------------------------------------------------------------

	@Test
	void reportsTheCallersTotalUnreadNotThePagesOwn() {
		when(repository.count(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID, false))
				.thenReturn(37L);
		when(repository.count(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID, true))
				.thenReturn(4L);
		when(repository.findForRecipient(any(), any(), anyString(), anyBoolean(), anyInt(), anyInt()))
				.thenReturn(List.of(inApp("NOTIF-1", false)));

		NotificationListDto result = service.list(CUSTOMER_ID, Role.CUSTOMER, null, 1, 20);

		assertEquals(4L, result.unreadCount(),
				"the bell badge is the caller's total, so it must not shrink as they page forward");
		assertEquals(37L, result.page().totalElements());
		assertEquals(2, result.page().totalPages());
		assertEquals(1, result.page().number());
	}

	@Test
	void asksForTheCountOnceWhenOnlyUnreadWasRequested() {
		when(repository.count(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID, true))
				.thenReturn(4L);
		when(repository.findForRecipient(any(), any(), anyString(), anyBoolean(), anyInt(), anyInt()))
				.thenReturn(List.of());

		NotificationListDto result = service.list(CUSTOMER_ID, Role.CUSTOMER, true, 0, 20);

		// Filtered total and unread total are the same number here, so one count answers both.
		assertEquals(4L, result.unreadCount());
		assertEquals(4L, result.page().totalElements());
		verify(repository, never()).count(any(), any(), anyString(), eq(false));
	}

	@Test
	void appliesSection26DefaultsAndCap() {
		when(repository.findForRecipient(any(), any(), anyString(), anyBoolean(), anyInt(), anyInt()))
				.thenReturn(List.of());

		assertEquals(20, service.list(CUSTOMER_ID, Role.CUSTOMER, null, null, null).page().size(),
				"no size supplied means 20");
		assertEquals(100, service.list(CUSTOMER_ID, Role.CUSTOMER, null, null, 500).page().size(),
				"a caller must not be able to ask for an unbounded page");
		assertEquals(0, service.list(CUSTOMER_ID, Role.CUSTOMER, null, -3, null).page().number(),
				"a negative page is a client bug, not a reason to fail the bell menu");
	}

	@Test
	void mapsOnlyTheSixFieldsSection91Publishes() {
		Notification stored = inApp("NOTIF-9", false);
		stored.setBookingRef("ORD-2026-1024");
		when(repository.findForRecipient(any(), any(), anyString(), anyBoolean(), anyInt(), anyInt()))
				.thenReturn(List.of(stored));

		NotificationDto item = service.list(CUSTOMER_ID, Role.CUSTOMER, null, 0, 20).items().get(0);

		assertEquals("NOTIF-9", item.notificationId());
		assertEquals(NotificationType.BOOKING_SCHEDULED, item.type());
		assertEquals("ORD-2026-1024", item.bookingRef());
		assertFalse(item.readStatus());
		assertNotNull(item.createdAt());
	}

	// --- §9.2: marking read --------------------------------------------------------------

	@Test
	void markingSomebodyElsesNotificationIsA404NotA403() {
		// The repository found nothing, because ownership is part of its query. The service
		// cannot tell "unknown" from "not yours" and §9.2 requires that it does not try.
		when(repository.markRead(any(), any(), anyString(), anyString())).thenReturn(false);

		ApiException thrown = assertThrows(ApiException.class,
				() -> service.markRead(CUSTOMER_ID, Role.CUSTOMER, "SOMEONE-ELSES"));

		assertSame(ErrorCode.NOTIFICATION_NOT_FOUND, thrown.getErrorCode());
		assertEquals(404, ErrorCode.NOTIFICATION_NOT_FOUND.getHttpStatus().value(),
				"§9.2 departs from §2.4's 403 on purpose, so ids cannot be probed for existence");
	}

	@Test
	void markingTheSameNotificationTwiceSucceedsBothTimes() {
		// Read status is not part of the repository's match, so an already-read notification
		// still matches and still reports true.
		when(repository.markRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID, "NOTIF-1"))
				.thenReturn(true);

		service.markRead(CUSTOMER_ID, Role.CUSTOMER, "NOTIF-1");
		service.markRead(CUSTOMER_ID, Role.CUSTOMER, "NOTIF-1");

		verify(repository, times(2))
				.markRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID, "NOTIF-1");
	}

	@Test
	void markAllReadSucceedsWithNothingToMark() {
		when(repository.markAllRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID))
				.thenReturn(0L);

		service.markAllRead(CUSTOMER_ID, Role.CUSTOMER);

		// An empty bell is not a failed request; the control must not error on a second press.
		verify(repository).markAllRead(Region.SOUTH, RecipientType.CUSTOMER, CUSTOMER_ID);
	}

	// --- §2.4: an administrator is entitled to ask, and has none -----------------------------

	@Test
	void anAdministratorGetsAnEmptyPageRatherThanAFault() {
		NotificationListDto result = service.list("ADMIN-1", Role.ADMIN, null, 0, 20);

		assertTrue(result.items().isEmpty());
		assertEquals(0L, result.unreadCount());
		assertEquals(0, result.page().totalPages());
		assertEquals(20, result.page().size(), "the page still echoes what was asked for");

		// Nothing is looked up: RecipientType has no value for an administrator, so there is
		// no store to look in and no question for the repository to answer.
		verifyNoInteractions(repository);
	}

	@Test
	void anAdministratorMarkingAnythingReadGetsThe404() {
		assertThrows(ApiException.class, () -> service.markRead("ADMIN-1", Role.ADMIN, "NOTIF-1"));

		verifyNoInteractions(repository);
	}

	@Test
	void anAdministratorMarkingAllReadIsAQuietNoOp() {
		service.markAllRead("ADMIN-1", Role.ADMIN);

		verifyNoInteractions(repository);
	}

	// --- Req 3.3.U2: raising one ------------------------------------------------------------

	@Test
	void raisesACustomerNotificationIntoTheirOwnStore() {
		NotificationDto raised = service.raise(NotificationCreateCommand.forCustomerBooking(
				CUSTOMER_ID, NotificationType.BOOKING_SCHEDULED, "Scheduled.", "ORD-2026-1024"));

		Notification written = saves.get(0);
		assertEquals(Region.SOUTH, written.getHomeRegion(),
				"a southern customer's notification belongs in the southern store");
		assertEquals(RecipientType.CUSTOMER, written.getRecipientType());
		assertEquals(CUSTOMER_ID, written.getRecipientIdentifier());
		assertEquals("ORD-2026-1024", written.getBookingRef());
		assertFalse(written.isReadStatus(), "a new notification has not been read");
		assertNotNull(raised.notificationId(),
				"the real-time push sends this without reading anything back");
	}

	@Test
	void anInAppNotificationIsDeliveredByBeingWritten() {
		service.raise(NotificationCreateCommand.forCustomerBooking(
				CUSTOMER_ID, NotificationType.BOOKING_CONFIRMED, "Confirmed.", "ORD-1"));

		Notification written = saves.get(0);
		// There is no provider to accept it and nothing for the sweeper to retry.
		assertEquals(DeliveryStatus.SENT, written.getDeliveryStatus());
		assertNotNull(written.getSentAt());
		assertFalse(written.isAwaitingDelivery());
	}

	@Test
	void everythingRaisedThisWayIsDeliverableOrDeliveredAlready() {
		service.raise(NotificationCreateCommand.forCustomerBooking(
				CUSTOMER_ID, NotificationType.BOOKING_CONFIRMED, "Confirmed.", "ORD-1"));

		Notification written = saves.get(0);
		assertEquals(DeliveryChannel.IN_APP, written.getDeliveryChannel());
		assertEquals(DeliveryStatus.SENT, written.getDeliveryStatus());
		assertNotNull(written.getSentAt());

		// The point of the restriction. A row the sweeper picks up but cannot resend gets
		// abandoned on the first pass, at ERROR -- and raise() had been able to create one.
		assertFalse(written.isAwaitingDelivery(),
				"nothing raised this way may ever reach the retry sweeper");
		assertNull(written.getUndeliveredBody());
	}

	@Test
	void raisingTwiceRecordsTwoNotifications() {
		NotificationCreateCommand command = NotificationCreateCommand.forCustomerBooking(
				CUSTOMER_ID, NotificationType.BOOKING_SCHEDULED, "Scheduled.", "ORD-1");

		service.raise(command);
		service.raise(command);

		// Not idempotent, deliberately: this command names no event, so there is nothing to
		// recognise a repeat by, and a second call is a genuine second notification. Naming
		// the event is what turns the same call into a replay -- see the section below.
		assertEquals(2, saves.size());
		verify(repository, never()).findBySourceEventId(any(), any());
	}

	// --- Req 3.3.U2, §9.3: delivered live, once per event -------------------------------------

	private static final String EVENT_ID = "evt-8f2c14a9";

	private NotificationCreateCommand scheduledByEvent() {
		return NotificationCreateCommand.forBookingEvent(EVENT_ID, CUSTOMER_ID,
				NotificationType.BOOKING_SCHEDULED,
				"Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
				"ORD-2026-1024");
	}

	@Test
	void pushesABookingNotificationOnlyAfterItIsSaved() {
		service.raise(scheduledByEvent());

		// The order is the contract (§9): a push before the write would let a customer who
		// taps the toast find nothing behind it if the write then failed.
		InOrder saveThenPush = inOrder(repository, gateway);
		saveThenPush.verify(repository).save(any());
		saveThenPush.verify(gateway).push(eq(CUSTOMER_ID), any(NotificationPushDto.class));
	}

	@Test
	void pushesTheSavedRowNotTheCommand() {
		service.raise(scheduledByEvent());

		ArgumentCaptor<NotificationPushDto> pushed = ArgumentCaptor.forClass(NotificationPushDto.class);
		verify(gateway).push(eq(CUSTOMER_ID), pushed.capture());

		Notification written = saves.get(0);
		assertEquals(written.getId(), pushed.getValue().notificationId(),
				"the client marks this id read through §9.2, so it has to be the stored one");
		assertEquals(NotificationType.BOOKING_SCHEDULED, pushed.getValue().type());
		assertEquals(written.getMessage(), pushed.getValue().message());
		assertEquals("ORD-2026-1024", pushed.getValue().bookingRef());
		assertEquals(written.getCreatedAt(), pushed.getValue().createdAt());
	}

	@Test
	void writesTheEventIdOntoTheNotification() {
		service.raise(scheduledByEvent());

		assertEquals(EVENT_ID, saves.get(0).getSourceEventId(),
				"without this on the row there is nothing for a replay to find");
	}

	@Test
	void aNotificationFromNoEventIsPushedButCarriesNoEventId() {
		service.raise(NotificationCreateCommand.forCustomerBooking(
				CUSTOMER_ID, NotificationType.BOOKING_CONFIRMED, "Confirmed.", "ORD-1"));

		verify(gateway).push(eq(CUSTOMER_ID), any(NotificationPushDto.class));
		assertNull(saves.get(0).getSourceEventId());
	}

	@Test
	void theSameEventRaisesOneNotificationAndOnePush() {
		// The repository answers from what has been saved, as the real one would.
		when(repository.findBySourceEventId(eq(Region.SOUTH), eq(EVENT_ID))).thenAnswer(
				invocation -> saves.stream()
						.filter(saved -> EVENT_ID.equals(saved.getSourceEventId()))
						.findFirst());

		NotificationDto first = service.raise(scheduledByEvent());
		NotificationDto replay = service.raise(scheduledByEvent());

		assertEquals(1, saves.size(), "a replay writes nothing");
		verify(gateway, times(1)).push(eq(CUSTOMER_ID), any(NotificationPushDto.class));
		assertEquals(first.notificationId(), replay.notificationId(),
				"the replay is answered with what the first delivery produced");
	}

	@Test
	void aLostRaceReturnsTheWinnerWithoutASecondPush() {
		// Both replays looked and found nothing; the index let one write. The one under test
		// is the loser: its save is refused, and the winner is what it finds on re-reading.
		Notification winner = inApp("NOTIF-WINNER", false);
		winner.setSourceEventId(EVENT_ID);
		doThrow(new DuplicateKeyException("sourceEventId")).when(repository).save(any());
		when(repository.findBySourceEventId(eq(Region.SOUTH), eq(EVENT_ID)))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(winner));

		NotificationDto answer = service.raise(scheduledByEvent());

		assertEquals("NOTIF-WINNER", answer.notificationId());
		verifyNoInteractions(gateway);
	}

	@Test
	void aDuplicateKeyFromSomethingElseIsNotSwallowed() {
		// No event id means no replay to fall back to. Whatever this collision was, it is
		// not one this guard understands, and hiding it would hide a real fault.
		doThrow(new DuplicateKeyException("something else")).when(repository).save(any());

		assertThrows(DuplicateKeyException.class, () -> service.raise(
				NotificationCreateCommand.forCustomerBooking(
						CUSTOMER_ID, NotificationType.BOOKING_CONFIRMED, "Confirmed.", "ORD-1")));

		verifyNoInteractions(gateway);
	}

	@Test
	void neverPushesAStaffNotification() {
		service.raise(new NotificationCreateCommand(RecipientType.STAFF, "STF-001", "HCM",
				NotificationType.STAFF_ACCOUNT_CREATED, "Welcome email sent to tuan@fixinh.vn",
				null));

		assertEquals(1, saves.size(), "still recorded -- it is the push that is withheld");
		verifyNoInteractions(gateway);
	}

	@Test
	void theWelcomeEmailIsNeverPushed() {
		service.sendStaffWelcomeEmail(COMMAND);

		// §9.3 pushes the two booking types and nothing else, and a staff member has no SBP
		// session to reach in any case.
		verifyNoInteractions(gateway);
	}

	/** An in-app notification for the customer under test, as the repository would return it. */
	private Notification inApp(String id, boolean read) {
		Notification notification = new Notification();
		notification.setId(id);
		notification.setRecipientType(RecipientType.CUSTOMER);
		notification.setRecipientIdentifier(CUSTOMER_ID);
		notification.setType(NotificationType.BOOKING_SCHEDULED);
		notification.setDeliveryChannel(DeliveryChannel.IN_APP);
		notification.setMessage("Your order is scheduled.");
		notification.setReadStatus(read);
		notification.setCreatedAt(Instant.parse("2026-08-11T03:20:00Z"));
		notification.setHomeRegion(Region.SOUTH);
		return notification;
	}

	/** A shallow copy, so later mutations of the same instance do not rewrite history. */
	private Notification copyOf(Notification source) {
		Notification copy = new Notification();
		copy.setId(source.getId());
		copy.setRecipientType(source.getRecipientType());
		copy.setRecipientIdentifier(source.getRecipientIdentifier());
		copy.setRecipientAddress(source.getRecipientAddress());
		copy.setType(source.getType());
		copy.setDeliveryChannel(source.getDeliveryChannel());
		copy.setMessage(source.getMessage());
		copy.setBookingRef(source.getBookingRef());
		copy.setSourceEventId(source.getSourceEventId());
		copy.setCreatedAt(source.getCreatedAt());
		copy.setReadStatus(source.isReadStatus());
		copy.setDeliveryStatus(source.getDeliveryStatus());
		copy.setSentAt(source.getSentAt());
		copy.setAttemptCount(source.getAttemptCount());
		copy.setLastAttemptAt(source.getLastAttemptAt());
		copy.setUndeliveredBody(source.getUndeliveredBody());
		copy.setHomeRegion(source.getHomeRegion());
		return copy;
	}
}