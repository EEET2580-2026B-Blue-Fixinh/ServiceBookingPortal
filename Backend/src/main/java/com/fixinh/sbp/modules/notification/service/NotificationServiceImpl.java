package com.fixinh.sbp.modules.notification.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.interfaces.internal.NotificationInternalService;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sending, and recording what happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationInternalService {

	private final StaffWelcomeEmailComposer composer;
	private final NotificationRegionResolver regionResolver;
	private final NotificationRepository notificationRepository;
	private final EmailClient emailClient;

	/**
	 * <b>Write, then send, then write again.</b>
	 */
	@Override
	public StaffWelcomeEmailResult sendStaffWelcomeEmail(StaffWelcomeEmailCommand command) {
		// Resolved first: without a store there is nowhere to file the notification, so there
		// is no point composing a message. Throws 400 PROVINCE_CODE_INVALID rather than
		// picking a shard.
		Region region = regionResolver.regionFor(
				RecipientType.STAFF, command.staffId(), command.branchProvinceCode());

		EmailMessage message = composer.compose(command);
		Notification notification = notificationRepository.save(
				pendingNotification(command, message, region));

		try {
			emailClient.send(message);
			notification.recordSuccess(Instant.now());
		}
		catch (EmailDeliveryException ex) {
			notification.recordFailedAttempt(Instant.now());

			// Recipient and cause only. The message body holds the initial password, and the
			// acceptance criteria say no credential reaches the application log.
			log.warn("Welcome email to {} was not accepted (attempt {}); it stays queued for "
					+ "retry", command.email(), notification.getAttemptCount(), ex);
		}

		Notification recorded = notificationRepository.save(notification);
		boolean sent = recorded.getDeliveryStatus() == DeliveryStatus.SENT;

		if (sent) {
			log.info("Welcome email sent to {} for staff {}", command.email(), command.staffId());
		}

		return new StaffWelcomeEmailResult(recorded.getId(), sent);
	}

	/**
	 * The row as it looks before anything is attempted.
	 */
	private Notification pendingNotification(StaffWelcomeEmailCommand command,
			EmailMessage message, Region region) {

		return Notification.builder()
				.recipientType(RecipientType.STAFF)
				.recipientIdentifier(command.staffId())
				.recipientAddress(command.email())
				.type(NotificationType.STAFF_ACCOUNT_CREATED)
				.deliveryChannel(DeliveryChannel.EMAIL)
				.message(composer.summaryFor(command))
				.readStatus(false)
				.createdAt(Instant.now())
				.deliveryStatus(DeliveryStatus.PENDING)
				.undeliveredBody(message.body())
				.homeRegion(region)
				.build();
	}
}