package com.fixinh.sbp.modules.integration.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.booking.dto.internal.BookingProjectionRefresh;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingProjectionView;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusChangedEvent;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto;
import com.fixinh.sbp.modules.integration.dto.external.EventReceiptDto;
import com.fixinh.sbp.modules.integration.interfaces.external.IntegrationExternalService;
import com.fixinh.sbp.modules.integration.model.EventDirection;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;
import com.fixinh.sbp.modules.integration.repository.IntegrationEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Receives booking changes from SMP (§11.7, Req 4.M3, 4.M1).
 *
 * <p>The order of the steps is the contract, so it is worth stating plainly: validate, resolve
 * the store, recognise a replay, record the event, refresh the projection, publish, mark
 * handled. Two of those placements carry the weight —
 *
 * <ul>
 *   <li><b>the refresh precedes the publish</b>, because §11.7 promises the customer is never
 *       told about a state SBP cannot yet display (Req 3.3.S5);</li>
 *   <li><b>marking handled comes last</b>, after every listener has returned. An event marked
 *       handled before its listeners ran would answer SMP's retry with
 *       {@code duplicate: true} for work that never finished, and the notification would be
 *       lost with no record that it was owed.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationServiceImpl implements IntegrationExternalService {

	private final BookingEventValidator validator;
	private final CustomerExternalService customerService;
	private final BookingInternalService bookingService;
	private final IntegrationEventRepository eventRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public EventReceiptDto receiveBookingEvent(BookingStatusWebhookDto event) {
		validator.validate(event);

		// SMP names a customer, never a shard (Req 4.U1). Unknown here is 404 rather than a
		// shard-resolution failure: §11.7 lists CUSTOMER_NOT_FOUND precisely so SMT can tell
		// "you do not know this person" from "you are broken".
		Region region = customerService.findHomeRegion(event.customerId())
				.orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND,
						"SBP has no customer " + event.customerId() + ", so there is no store to "
								+ "refresh and nobody to notify."));

		Optional<IntegrationEvent> earlier = eventRepository.findInbound(region, event.eventId());
		if (earlier.isPresent() && earlier.get().isHandled()) {
			log.info("Event {} was already handled; the replay changes nothing", event.eventId());
			return EventReceiptDto.duplicate(event.eventId());
		}

		Optional<IntegrationEvent> recorded = earlier.isPresent()
				? earlier
				: record(event, region);
		if (recorded.isEmpty()) {
			// A second delivery of this event is being handled right now, in another thread or
			// another instance. This delivery genuinely changes nothing, which is exactly what
			// the receipt says -- and if the one in flight fails, SMP sees that failure on its
			// own request and retries it.
			return EventReceiptDto.duplicate(event.eventId());
		}
		IntegrationEvent inbound = recorded.get();

		BookingProjectionView refreshed = bookingService.refreshProjection(
				event.bookingRef(), refreshFrom(event));

		// Belt and braces on Req A.2: the booking's own owner decides who hears about it, not
		// the customerId in the payload. They disagree only if SMP has the two systems'
		// records crossed, and a notification sent to the wrong customer would be a privacy
		// fault, not a glitch.
		if (!refreshed.customerId().equals(event.customerId())) {
			throw new ApiException(ErrorCode.EVENT_MALFORMED,
					"Booking " + event.bookingRef() + " does not belong to customer "
							+ event.customerId() + " in SBP's records. Nothing was changed.");
		}

		// Synchronous by design: the listeners run here, before this method returns, which is
		// what puts them after the refresh above. Any exception they raise reaches SMP as a
		// 5xx and leaves the event unmarked, so the retry runs the whole thing again.
		eventPublisher.publishEvent(internalEvent(event, refreshed));

		inbound.markHandled(Instant.now());
		eventRepository.save(inbound);

		log.info("Handled {} for {}: status {}{}", event.eventId(), event.bookingRef(),
				refreshed.status(), refreshed.applied() ? "" : " (projection already newer)");

		return EventReceiptDto.handled(event.eventId());
	}

	/**
	 * Writes the event down before acting on it, so a crash mid-handling leaves a trace.
	 *
	 * <p>Empty means a {@link DuplicateKeyException}: a second delivery of the same event is
	 * being handled right now, in another thread or another instance. Doing the work twice
	 * concurrently is the one thing the {@code eventId} key exists to prevent, so this
	 * delivery stands down rather than racing.
	 */
	private Optional<IntegrationEvent> record(BookingStatusWebhookDto event, Region region) {
		IntegrationEvent inbound = IntegrationEvent.builder()
				.eventId(event.eventId())
				.direction(EventDirection.IN)
				.eventType(event.eventType())
				.bookingRef(event.bookingRef())
				.customerId(event.customerId())
				.occurredAt(event.occurredAt())
				.receivedAt(Instant.now())
				.homeRegion(region)
				.build();

		try {
			return Optional.of(eventRepository.insert(inbound));
		}
		catch (DuplicateKeyException concurrent) {
			log.info("Event {} is already being handled elsewhere", event.eventId());
			return Optional.empty();
		}
	}

	/** What SMP's payload means to the booking module. */
	private static BookingProjectionRefresh refreshFrom(BookingStatusWebhookDto event) {
		BookingStatusWebhookDto.BookingPayload payload = event.payload();

		return new BookingProjectionRefresh(
				BookingStatus.valueOf(payload.status()),
				payload.scheduledStartAt(),
				payload.estimatedDurationMinutes(),
				payload.technicians(),
				billFrom(payload),
				event.occurredAt());
	}

	private static BookingProjectionRefresh.BillRefresh billFrom(
			BookingStatusWebhookDto.BookingPayload payload) {

		// The validator has already refused half a bill, so either both numbers are here or
		// there is no bill at all.
		if (payload.serviceFee() == null) {
			return null;
		}
		return new BookingProjectionRefresh.BillRefresh(payload.serviceFee(),
				payload.materialCost(), payload.billImageUrl(), payload.billIssuedAt(),
				payload.totalCost());
	}

	/**
	 * The event other modules react to, built from the <b>refreshed projection</b> rather than
	 * from the payload.
	 *
	 * <p>They are the same thing on a first delivery, and deliberately not on a replay of an
	 * older change: the projection keeps the newer state, and a listener told the payload's
	 * state would compose a message describing a booking as it no longer is. What was stored
	 * is what the customer will see when they tap the notification, so it is what the
	 * notification should say.
	 */
	private static BookingStatusChangedEvent internalEvent(BookingStatusWebhookDto event,
			BookingProjectionView refreshed) {

		return new BookingStatusChangedEvent(
				event.eventId(),
				refreshed.bookingRef(),
				refreshed.customerId(),
				refreshed.status(),
				refreshed.scheduledStartAt(),
				refreshed.estimatedDurationMinutes(),
				event.occurredAt());
	}
}