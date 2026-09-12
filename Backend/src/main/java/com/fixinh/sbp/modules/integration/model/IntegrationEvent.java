package com.fixinh.sbp.modules.integration.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fixinh.sbp.common.sharding.Region;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One integration message, as Milestone 1's {@code integrationEvents} collection records it.
 * For now that means one inbound booking event from SMP (§11.7).
 *
 * <p><b>The record is the replay guard.</b> §11.7 lets SMP retry freely on any 5xx or timeout
 * and promises that the same {@code eventId} twice "changes nothing". That promise needs a
 * memory of which ids have been seen, and it has to be one that survives a restart, which is
 * why it is a document rather than a set in a field.
 *
 * <p>The id is the event's own. SMP guarantees it unique per event, and making it Mongo's
 * {@code _id} turns that guarantee into a constraint: two replays arriving in the same
 * instant cannot both insert, and the loser learns it from the database rather than from a
 * check that raced.
 *
 * <p>{@code handledAt} separates <em>seen</em> from <em>done</em>. A row with no
 * {@code handledAt} is an event that was accepted but whose handling did not finish —
 * the process died, or a listener threw and SMP was told 5xx. A replay of that one must run
 * again; only a replay of a handled one is answered with {@code duplicate: true}. The TTL
 * index is on this field for the same reason: an unfinished event is never expired out from
 * under the retry that would complete it.
 *
 * <p>Filed in the customer's regional store, because the booking it concerns and the
 * notification it produces both live there (Milestone 1: "sharded with its customer").
 */
@Document(collection = "integrationEvents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationEvent {

	/** §11.7's {@code eventId}, as sent. SMP's uniqueness promise becomes Mongo's constraint. */
	@Id
	private String eventId;

	private EventDirection direction;

	/** §11.7's {@code eventType}, kept as text: SBP records what it was told, it does not judge it. */
	private String eventType;

	private String bookingRef;

	/** The recipient of anything raised from this, and the reason the row is in this store. */
	private String customerId;

	/** When the change happened in SMP, not when it was delivered. */
	private Instant occurredAt;

	private Instant receivedAt;

	/** Null until every listener has returned. Null after a crash or a refused notification. */
	private Instant handledAt;

	/** Which store owns this row — the customer's. */
	private Region homeRegion;

	/** Every listener has returned; a replay from here on changes nothing. */
	public void markHandled(Instant when) {
		this.handledAt = when;
	}

	/** True once handling finished. False for a row that was written but never completed. */
	public boolean isHandled() {
		return handledAt != null;
	}
}
