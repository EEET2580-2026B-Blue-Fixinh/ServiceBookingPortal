package com.fixinh.sbp.modules.notification.dto.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;

import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises with a plain mapper rather than reading the record's fields back, because the
 * contract is the JSON on the wire, not the Java. The application sets no
 * {@code spring.jackson.*} overrides, so a default mapper is representative of what the
 * STOMP message converter produces; the round-trip test confirms it against the live
 * channel.
 */
class NotificationPushDtoTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Set<String> SECTION_9_3_FIELDS =
			Set.of("notificationId", "type", "message", "bookingRef", "createdAt");

	@Test
	void carriesExactlyTheFieldsSection93Lists() {
		Notification saved = scheduled().build();

		Map<?, ?> json = MAPPER.readValue(
				MAPPER.writeValueAsString(NotificationPushDto.from(saved)), Map.class);

		assertEquals(SECTION_9_3_FIELDS, json.keySet(),
				"no readStatus, no deliveryStatus, nothing the wire format does not list");
		assertEquals("6713cd45ef67ab8901234567", json.get("notificationId"));
		assertEquals("BOOKING_SCHEDULED", json.get("type"));
		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
				json.get("message"));
		assertEquals("ORD-2026-1024", json.get("bookingRef"));
		assertEquals("2026-08-11T03:20:00Z", json.get("createdAt"),
				"ISO-8601 with a Z, as every other timestamp in the API");
	}

	@Test
	void mapsFromTheSavedNotificationWithoutItsReadState() {
		Notification saved = scheduled().readStatus(true).build();

		NotificationPushDto dto = NotificationPushDto.from(saved);

		assertEquals(saved.getId(), dto.notificationId());
		assertEquals(saved.getType(), dto.type());
		assertEquals(saved.getMessage(), dto.message());
		assertEquals(saved.getBookingRef(), dto.bookingRef());
		assertEquals(saved.getCreatedAt(), dto.createdAt());
		// There is no accessor to assert against -- which is the point.
	}

	@Test
	void omitsBookingRefEntirelyWhenThereIsNoBooking() {
		Notification saved = scheduled().bookingRef(null).build();

		NotificationPushDto dto = NotificationPushDto.from(saved);
		Map<?, ?> json = MAPPER.readValue(MAPPER.writeValueAsString(dto), Map.class);

		assertNull(dto.bookingRef());
		assertFalse(json.containsKey("bookingRef"),
				"absent, not null -- the same rule the §9.1 list follows");
	}

	// --- Fixtures --------------------------------------------------------------------------

	private static Notification.NotificationBuilder scheduled() {
		return Notification.builder()
				.id("6713cd45ef67ab8901234567")
				.recipientType(RecipientType.CUSTOMER)
				.recipientIdentifier("66f1a2b3c4d5e6f708192a3b")
				.type(NotificationType.BOOKING_SCHEDULED)
				.deliveryChannel(DeliveryChannel.IN_APP)
				.message("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.")
				.bookingRef("ORD-2026-1024")
				.readStatus(false)
				.createdAt(Instant.parse("2026-08-11T03:20:00Z"))
				.homeRegion(Region.SOUTH);
	}
}
