package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;

/**
 * The send path, with the provider and the database mocked and everything else real.
 *
 * <p>The repository mock keeps every version it is handed, so the assertions can check the
 * order of writes as well as the final state — which is the whole point of saving before the
 * provider is called.
 */
class NotificationServiceImplTest {

	private static final String PASSWORD = "Tmp!Passw0rd";

	private static final StaffWelcomeEmailCommand COMMAND = new StaffWelcomeEmailCommand(
			"STF-001", "Nguyen Minh Tuan", "tuan@fixinh.vn", "TECHNICIAN", PASSWORD, "HCM");

	private final List<Notification> saves = new ArrayList<>();

	private EmailClient emailClient;
	private ShardDirectoryRepository shardDirectory;
	private NotificationServiceImpl service;

	@BeforeEach
	void setUp() {
		saves.clear();
		emailClient = mock(EmailClient.class);
		shardDirectory = mock(ShardDirectoryRepository.class);

		when(shardDirectory.findRegion(SubjectType.STAFF, "STF-001")).thenReturn(Optional.empty());
		when(shardDirectory.register(SubjectType.STAFF, "STF-001", Region.SOUTH))
				.thenReturn(Region.SOUTH);

		NotificationRepository repository = mock(NotificationRepository.class);
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
				new NotificationRegionResolver(shardDirectory, new RegionResolver()),
				repository,
				emailClient);
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

		try {
			service.sendStaffWelcomeEmail(nowhere);
		}
		catch (RuntimeException expected) {
			// 400 PROVINCE_CODE_INVALID from the region resolver.
		}

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
		copy.setCreatedAt(source.getCreatedAt());
		copy.setDeliveryStatus(source.getDeliveryStatus());
		copy.setSentAt(source.getSentAt());
		copy.setAttemptCount(source.getAttemptCount());
		copy.setLastAttemptAt(source.getLastAttemptAt());
		copy.setUndeliveredBody(source.getUndeliveredBody());
		copy.setHomeRegion(source.getHomeRegion());
		return copy;
	}
}