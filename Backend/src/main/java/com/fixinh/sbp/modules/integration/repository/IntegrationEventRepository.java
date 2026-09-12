package com.fixinh.sbp.modules.integration.repository;

import java.util.Optional;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;

/**
 * Inbound integration events, in the store of the customer they concern.
 *
 * <p>Every method names a region: an event names its customer, the customer's store has been
 * resolved before anything here is asked, and a replay can only have been filed in that one
 * place. Asking both stores would be slower and, worse, would let a region mismatch answer
 * "never seen" for an event that was.
 */
public interface IntegrationEventRepository {

	/**
	 * Writes a brand-new event, and only a brand-new one.
	 *
	 * <p>An insert rather than a save: {@code save} on an existing {@code _id} would overwrite
	 * the row that says the event was already handled, and the replay guard would be the
	 * thing a replay destroyed. A second insert of the same id is refused by the store with
	 * {@link org.springframework.dao.DuplicateKeyException}, which is the answer the caller
	 * wants — it means someone else got there first.
	 *
	 * @throws IllegalStateException when the event has no region, for the same reason every
	 *         other sharded repository refuses: a row in a guessed store is one nobody finds
	 */
	IntegrationEvent insert(IntegrationEvent event);

	/**
	 * Rewrites an event that already exists — used for marking it handled.
	 *
	 * @throws IllegalStateException when the event has no region
	 */
	IntegrationEvent save(IntegrationEvent event);

	/**
	 * The inbound event with this id, if SBP has seen it before.
	 *
	 * @return empty on a first delivery — the ordinary answer — or when the id is blank, which
	 *         can match nothing
	 * @throws IllegalStateException when the region is not set. "Not found" from a guessed
	 *         store reads exactly like a first delivery, which is the one thing a replay must
	 *         never be mistaken for
	 */
	Optional<IntegrationEvent> findInbound(Region region, String eventId);
}
