package com.fixinh.sbp.modules.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.dto.PageResponse;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.notification.dto.external.NotificationDto;
import com.fixinh.sbp.modules.notification.dto.external.NotificationListDto;
import com.fixinh.sbp.modules.notification.dto.external.NotificationPushDto;
import com.fixinh.sbp.modules.notification.dto.internal.NotificationCreateCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.interfaces.external.NotificationExternalService;
import com.fixinh.sbp.modules.notification.interfaces.internal.NotificationInternalService;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;
import com.fixinh.sbp.modules.notification.websocket.NotificationGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sending, and recording what happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationInternalService, NotificationExternalService {

	private final StaffWelcomeEmailComposer composer;
	private final NotificationRegionResolver regionResolver;
	private final NotificationRepository notificationRepository;
	private final EmailClient emailClient;
	private final NotificationGateway gateway;

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

	// --- Raising one, for Integration and Admin -------------------------------

	/**
	 * <b>Look, write, push</b> — and the push only after the write has returned, because §9's
	 * promise that a notification survives a customer being offline is only true of a row that
	 * exists before anyone tries to reach them.
	 */
	@Override
	public NotificationDto raise(NotificationCreateCommand command) {
		Region region = storeFor(command);

		// A replayed SMP event finds the notification it already produced and stops here: no
		// second row, no second push. Only a command that names its event can be a replay.
		Optional<Notification> earlier = raisedEarlier(region, command);
		if (earlier.isPresent()) {
			return replay(earlier.get(), command);
		}

		Notification saved;
		try {
			saved = notificationRepository.save(inAppNotification(command, region));
		}
		catch (DuplicateKeyException ex) {
			// Two replays of one event arrived together, both looked and found nothing, and
			// the sparse unique index let only one of them write. This is the other one, and
			// its outcome already exists -- it has been pushed by the winner, so it is not
			// pushed again here.
			Notification winner = raisedEarlier(region, command).orElseThrow(() -> ex);
			return replay(winner, command);
		}

		if (deliveredLive(saved)) {
			gateway.push(saved.getRecipientIdentifier(), NotificationPushDto.from(saved));
		}

		log.debug("Raised {} notification {} for {} {}", command.type(), saved.getId(),
				command.recipientType(), command.recipientIdentifier());

		return NotificationDto.from(saved);
	}

	/**
	 * The notification this command's event already produced, if the command names an event
	 * and it has been seen. Empty for a command from no event: nothing to look up, so nothing
	 * to find.
	 */
	private Optional<Notification> raisedEarlier(Region region, NotificationCreateCommand command) {
		if (command.sourceEventId() == null) {
			return Optional.empty();
		}
		return notificationRepository.findBySourceEventId(region, command.sourceEventId());
	}

	/** The answer to a repeated event: what the first delivery produced, and nothing more. */
	private NotificationDto replay(Notification existing, NotificationCreateCommand command) {
		log.info("Event {} already raised notification {}; the replay changes nothing",
				command.sourceEventId(), existing.getId());
		return NotificationDto.from(existing);
	}

	/**
	 * Whether a notification goes over the real-time channel the moment it is saved (§9.3).
	 *
	 * <p>The type carries the decision; the recipient check is the belt to its braces. Only a
	 * customer has an SBP session to push to, and {@code convertAndSendToUser} resolving a
	 * staff id would simply find no session -- harmless, but a push that can never land is
	 * not worth attempting.
	 */
	private boolean deliveredLive(Notification notification) {
		return notification.getType().isPushedLive()
				&& notification.getRecipientType() == RecipientType.CUSTOMER;
	}

	/**
	 * The row for a notification whose delivery is the write itself.
	 *
	 * <p>IN_APP and SENT are fixed here rather than taken from the command: there is no
	 * provider to accept it, nothing for the retry sweeper to do, and no address or rendered
	 * body that a resend would need. An EMAIL record raised this way would be abandoned on
	 * the sweeper's first pass for lacking exactly those.
	 */
	private Notification inAppNotification(NotificationCreateCommand command, Region region) {
		Instant now = Instant.now();

		return Notification.builder()
				.recipientType(command.recipientType())
				.recipientIdentifier(command.recipientIdentifier())
				.type(command.type())
				.deliveryChannel(DeliveryChannel.IN_APP)
				.message(command.message())
				.bookingRef(command.bookingRef())
				.sourceEventId(command.sourceEventId())
				.readStatus(false)
				.createdAt(now)
				.deliveryStatus(DeliveryStatus.SENT)
				.sentAt(now)
				.homeRegion(region)
				.build();
	}

	/**
	 * Where a newly raised notification is filed.
	 */
	private Region storeFor(NotificationCreateCommand command) {
		return switch (command.recipientType()) {
			case CUSTOMER -> regionResolver.regionForCustomer(command.recipientIdentifier());
			case STAFF -> regionResolver.regionFor(RecipientType.STAFF,
					command.recipientIdentifier(), command.provinceCode());
		};
	}

	// --- The recipient's own history -----------------------------------------
	@Override
	public NotificationListDto list(String callerId, Role callerRole, Boolean unreadOnly,
			Integer page, Integer size) {

		int pageNumber = PageResponse.normalisePage(page);
		int pageSize = PageResponse.normaliseSize(size);
		boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);

		Optional<Region> store = storeFor(callerId, callerRole);
		if (store.isEmpty()) {
			return NotificationListDto.empty(pageNumber, pageSize);
		}
		Region region = store.get();

		long totalElements = notificationRepository.count(
				region, RecipientType.CUSTOMER, callerId, onlyUnread);

		// With unreadOnly the two numbers §9.1 reports are the same count, so ask once. Without
		// it they differ, and unreadCount is the caller's total -- not this page's, and not the
		// filtered total -- because the badge must not change as the reader pages forward.
		long unread = onlyUnread
				? totalElements
				: notificationRepository.count(region, RecipientType.CUSTOMER, callerId, true);

		List<NotificationDto> items = notificationRepository
				.findForRecipient(region, RecipientType.CUSTOMER, callerId, onlyUnread,
						pageNumber, pageSize)
				.stream()
				.map(NotificationDto::from)
				.toList();

		return new NotificationListDto(items, unread,
				PageResponse.Metadata.of(pageNumber, pageSize, totalElements));
	}

	@Override
	public void markRead(String callerId, Role callerRole, String notificationId) {
		// An administrator owns no notifications, so every id is somebody else's -- which §9.2
		// answers with the same 404 as an id that never existed. Reaching the same conclusion
		// without a lookup is not a shortcut; there is genuinely nothing to look in.
		boolean marked = storeFor(callerId, callerRole)
				.map(region -> notificationRepository.markRead(
						region, RecipientType.CUSTOMER, callerId, notificationId))
				.orElse(false);

		if (!marked) {
			// No detail about which of the two cases this was. Saying would be the disclosure
			// §9.2 chose 404 over 403 to avoid.
			throw new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}
	}

	@Override
	public void markAllRead(String callerId, Role callerRole) {
		storeFor(callerId, callerRole).ifPresent(region -> {
			long marked = notificationRepository.markAllRead(
					region, RecipientType.CUSTOMER, callerId);

			if (marked > 0) {
				log.debug("Marked {} notifications read for customer {}", marked, callerId);
			}
		});
	}

	@Override
	public long unreadCount(String callerId, Role callerRole) {
		return storeFor(callerId, callerRole)
				.map(region -> notificationRepository.count(
						region, RecipientType.CUSTOMER, callerId, true))
				.orElse(0L);
	}

	/**
	 * The store to read this caller's notifications from, or empty when they can have none.
	 */
	private Optional<Region> storeFor(String callerId, Role callerRole) {
		if (callerRole != Role.CUSTOMER) {
			return Optional.empty();
		}
		return Optional.of(regionResolver.regionForCustomer(callerId));
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