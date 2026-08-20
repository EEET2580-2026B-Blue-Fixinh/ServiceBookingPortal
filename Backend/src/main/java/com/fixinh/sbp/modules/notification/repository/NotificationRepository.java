package com.fixinh.sbp.modules.notification.repository;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.modules.notification.model.Notification;

/**
 * Notification access, spanning both regional stores.
 */
public interface NotificationRepository {

	/**
	 * Writes to the store named by the notification's {@code homeRegion}, and only that one.
	 *
	 * @throws IllegalStateException when the region is not set. Refusing beats defaulting: a
	 *         notification filed in the wrong store is invisible to the recipient forever, and
	 *         the failure is silent at the point it is caused.
	 */
	Notification save(Notification notification);

	/**
	 * By identifier, from whichever store holds it.
	 *
	 * <p>The id carries no region, so this may ask both. Acceptable here — it happens after
	 * authentication and once per request, not on a hot path.
	 */
	Optional<Notification> findById(String id);

	/**
	 * Emails that have not been delivered yet, oldest attempt first, from both stores.
	 *
	 * <p>Matches {@code PENDING} and {@code FAILED} on the {@code EMAIL} channel — the two
	 * states that still owe somebody a message. {@code IN_APP} is excluded because delivery
	 * there is the write itself; there is nothing to try again.
	 *
	 * <p><b>The attempt cap is not applied here, on purpose.</b> Filtering it out in the query
	 * would leave exhausted rows sitting at {@code FAILED} forever, and {@code ABANDONED} is
	 * precisely the state a human is supposed to notice. The sweeper reads the cap and marks
	 * them.
	 *
	 * <p>Rows never attempted sort first, because their {@code lastAttemptAt} is null. That is
	 * the right order: those are the ones a crash caught mid-send.
	 *
	 * @param limit how many to take <b>from each store</b>, so at most twice this comes back
	 */
	List<Notification> findOutstandingEmails(int limit);
}