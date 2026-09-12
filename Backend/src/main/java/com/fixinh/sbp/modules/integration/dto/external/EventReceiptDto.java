package com.fixinh.sbp.modules.integration.dto.external;

/**
 * §11.7's 202 body: {@code { "eventId": "...", "accepted": true, "duplicate": false }}.
 *
 * <p>{@code accepted} is always true, which is not redundant — it is what distinguishes this
 * from §2.7's error body, and it lets SMP treat any 202 as success without inspecting the
 * other fields.
 *
 * @param duplicate true when this event had already been handled, so nothing changed. §11.7
 *        promises a replay is answered rather than refused, which is what makes SMP's retry
 *        on any 5xx or timeout safe
 */
public record EventReceiptDto(String eventId, boolean accepted, boolean duplicate) {

	/** A first delivery, now handled. */
	public static EventReceiptDto handled(String eventId) {
		return new EventReceiptDto(eventId, true, false);
	}

	/** A replay of an event already handled. Nothing was written and nothing was sent. */
	public static EventReceiptDto duplicate(String eventId) {
		return new EventReceiptDto(eventId, true, true);
	}
}
