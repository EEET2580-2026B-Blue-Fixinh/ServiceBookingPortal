package com.fixinh.sbp.modules.notification.repository;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.RecipientType;

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

	// --- The recipient's own history -----------------------------------------
	// Every method below names a region, unlike the three above. Those are asked without a
	// recipient in hand and so have to try both stores; these are asked on behalf of a
	// signed-in caller whose store the service has already resolved. Passing it means one
	// store is read instead of two, and another region's rows cannot come back by accident.

	/**
	 * One page of a recipient's notifications, newest first.
	 *
	 * @param unreadOnly restricts to {@code readStatus: false}, which is what the bell menu
	 *        asks for
	 * @param page zero-based, already normalised by the caller
	 * @param size already normalised and capped by the caller
	 */
	List<Notification> findForRecipient(Region region, RecipientType recipientType,
			String recipientIdentifier, boolean unreadOnly, int page, int size);

	/**
	 * How many notifications the same filter matches.
	 */
	long count(Region region, RecipientType recipientType, String recipientIdentifier,
			boolean unreadOnly);

	/**
	 * Marks one notification read, if it belongs to this recipient (§9.2).
	 *
	 * @return whether a notification matched. False means unknown or someone else's, and the
	 *         caller must not try to tell those apart
	 */
	boolean markRead(Region region, RecipientType recipientType, String recipientIdentifier,
			String notificationId);

	/**
	 * Marks every unread notification for this recipient read (§9.2's note), in one write.
	 *
	 * @return how many were changed. Zero is an ordinary outcome — everything was already
	 *         read — and not an error
	 */
	long markAllRead(Region region, RecipientType recipientType, String recipientIdentifier);
}