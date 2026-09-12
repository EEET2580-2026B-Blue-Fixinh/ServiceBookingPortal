package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.file.FileWritingEmailClient;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

/**
 * Req 3.1.S5's last acceptance criterion, tested as it is written: no credential or password
 * appears in the application logs.
 *
 * <p>Everything is real except the database. The composer writes the actual letter, and
 * {@link FileWritingEmailClient} writes it to a temporary outbox — or fails to, when a file is
 * sitting where the outbox should be, which is how the refused paths are reached. Every line
 * any {@code com.fixinh.sbp} logger emits, at every level, is captured and searched for the
 * password, stack traces included.
 */
class WelcomeEmailLoggingTest {

	/** Distinctive enough that finding it anywhere can only mean it leaked. */
	private static final String PASSWORD = "Xk3m-Pq9a-Z2vT";

	private static final StaffWelcomeEmailCommand COMMAND = new StaffWelcomeEmailCommand(
			"TEC-7F3A2C", "Nguyen Minh Tuan", "tuan@fixinh.vn", "TECHNICIAN", PASSWORD, "HCM");

	private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
	private Logger applicationLogger;
	private Level previousLevel;
	private boolean previousAdditivity;

	@BeforeEach
	void captureEverythingTheApplicationLogs() {
		// logback-test.xml turns com.fixinh.sbp OFF to keep ordinary runs quiet. This test needs
		// the opposite: every line at every level, including the DEBUG the dev profile prints.
		// Additivity goes off so the captured lines do not also flood the console.
		applicationLogger = (Logger) LoggerFactory.getLogger("com.fixinh.sbp");
		previousLevel = applicationLogger.getLevel();
		previousAdditivity = applicationLogger.isAdditive();

		captured.setContext(applicationLogger.getLoggerContext());
		captured.start();
		applicationLogger.addAppender(captured);
		applicationLogger.setLevel(Level.ALL);
		applicationLogger.setAdditive(false);
	}

	@AfterEach
	void restoreLogging() {
		applicationLogger.detachAppender(captured);
		applicationLogger.setLevel(previousLevel);
		applicationLogger.setAdditive(previousAdditivity);
		captured.stop();
	}

	@Test
	void aSuccessfulSendNamesTheRecipientButNeverThePassword(@TempDir Path tempDir)
			throws IOException {

		Path outbox = tempDir.resolve("mail");

		StaffWelcomeEmailResult result = serviceWritingTo(outbox).sendStaffWelcomeEmail(COMMAND);

		assertTrue(result.sent());
		// The letter is the one place the password belongs. Finding it there proves there was a
		// password in play to leak, so the silence asserted below means something.
		assertTrue(letterIn(outbox).contains(PASSWORD));
		assertNoLineContainsThePassword();
	}

	@Test
	void aRefusedSendLogsItsCauseButNeverThePassword(@TempDir Path tempDir) throws IOException {
		Path blocked = Files.createFile(tempDir.resolve("mail"));

		StaffWelcomeEmailResult result = serviceWritingTo(blocked).sendStaffWelcomeEmail(COMMAND);

		assertFalse(result.sent());
		assertTrue(captured.list.stream().anyMatch(event -> event.getThrowableProxy() != null),
				"the refusal is logged with its stack trace, which has to be searched as well");
		assertNoLineContainsThePassword();
	}

	@Test
	void theRetrySweepNeverLogsThePassword(@TempDir Path tempDir) throws IOException {
		SbpProperties properties = properties(Files.createFile(tempDir.resolve("mail")));
		StaffWelcomeEmailComposer composer = new StaffWelcomeEmailComposer(properties);
		String letter = composer.compose(COMMAND).body();
		int lastAttempt = properties.getEmail().getRetry().getMaxAttempts() - 1;

		// One retry that fails again and stays queued, and one that fails for the last time and
		// is abandoned: the sweep's warning and its loudest error, both about this letter.
		NotificationRepository repository = mock(NotificationRepository.class);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(
				List.of(outstanding(letter, 1), outstanding(letter, lastAttempt)));

		new NotificationRetryScheduler(repository, composer, new FileWritingEmailClient(properties),
				properties).retryOutstandingEmails();

		assertTrue(captured.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR),
				"the abandonment is logged at ERROR, so that path was searched too");
		assertNoLineContainsThePassword();
	}

	/**
	 * Searches every captured line and stack trace. Refuses to pass on an empty capture, and
	 * requires that some line names the recipient — so a broken appender cannot turn this into
	 * a test that passes by seeing nothing.
	 */
	private void assertNoLineContainsThePassword() {
		assertFalse(captured.list.isEmpty(), "nothing was captured; the test would pass vacuously");
		assertTrue(captured.list.stream()
						.anyMatch(event -> event.getFormattedMessage().contains(COMMAND.email())),
				"the lines about this letter name its recipient, so they were captured");

		for (ILoggingEvent event : captured.list) {
			String rendered = event.getFormattedMessage();
			if (event.getThrowableProxy() != null) {
				rendered += "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy());
			}
			// Names the logger and level, not the line: by then the line holds a password.
			assertFalse(rendered.contains(PASSWORD), event.getLevel() + " line from "
					+ event.getLoggerName() + " contains the password");
		}
	}

	private NotificationServiceImpl serviceWritingTo(Path outbox) {
		SbpProperties properties = properties(outbox);

		ShardDirectoryRepository directory = mock(ShardDirectoryRepository.class);
		when(directory.findRegion(SubjectType.STAFF, COMMAND.staffId()))
				.thenReturn(Optional.of(Region.SOUTH));

		NotificationRepository repository = mock(NotificationRepository.class);
		when(repository.save(any())).thenAnswer(invocation -> {
			Notification saved = invocation.getArgument(0);
			if (saved.getId() == null) {
				saved.setId("NOTIF-1");
			}
			return saved;
		});

		return new NotificationServiceImpl(
				new StaffWelcomeEmailComposer(properties),
				new NotificationRegionResolver(directory, new RegionResolver(),
						mock(CustomerExternalService.class)),
				repository,
				new FileWritingEmailClient(properties));
	}

	/** A failed welcome email, well past its backoff, still holding the letter it owes. */
	private Notification outstanding(String letter, int attemptsSoFar) {
		return Notification.builder()
				.id("NOTIF-" + attemptsSoFar)
				.recipientType(RecipientType.STAFF)
				.recipientIdentifier(COMMAND.staffId())
				.recipientAddress(COMMAND.email())
				.type(NotificationType.STAFF_ACCOUNT_CREATED)
				.deliveryChannel(DeliveryChannel.EMAIL)
				.message("Welcome email for Nguyen Minh Tuan (TECHNICIAN) to tuan@fixinh.vn")
				.deliveryStatus(DeliveryStatus.FAILED)
				.attemptCount(attemptsSoFar)
				.createdAt(Instant.now().minus(Duration.ofHours(3)))
				.lastAttemptAt(Instant.now().minus(Duration.ofHours(2)))
				.undeliveredBody(letter)
				.homeRegion(Region.SOUTH)
				.build();
	}

	private static SbpProperties properties(Path outbox) {
		SbpProperties properties = new SbpProperties();
		properties.getEmail().setOutboxDirectory(outbox.toString());
		properties.getEmail().setFromAddress("no-reply@fixinh.vn");
		properties.getEmail().setFromName("Fixinh");
		properties.getEmail().setStaffSignInUrl("https://smp.fixinh.vn/login");
		return properties;
	}

	private static String letterIn(Path outbox) throws IOException {
		try (Stream<Path> files = Files.list(outbox)) {
			return Files.readString(files.findFirst().orElseThrow(), StandardCharsets.UTF_8);
		}
	}
}