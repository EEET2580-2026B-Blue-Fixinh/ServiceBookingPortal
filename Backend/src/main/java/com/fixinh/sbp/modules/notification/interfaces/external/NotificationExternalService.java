package com.fixinh.sbp.modules.notification.interfaces.external;

import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.modules.notification.dto.external.NotificationListDto;

/**
 * What the notification module's controller may call.
 */
public interface NotificationExternalService {

	/**
	 * One page of the caller's notifications, newest first (§9.1).
	 *
	 * @param unreadOnly null is false — §9.1's stated default
	 * @param page null is the first page; negative is clamped to it
	 * @param size null is 20, and anything above 100 is capped (§2.6)
	 */
	NotificationListDto list(String callerId, Role callerRole, Boolean unreadOnly,
			Integer page, Integer size);

	/**
	 * Marks one notification read (§9.2). Idempotent: doing it twice succeeds twice.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException 404 {@code NOTIFICATION_NOT_FOUND}
	 *         when the id is unknown <b>or belongs to someone else</b>. §9.2 makes those the
	 *         same answer on purpose, so ids cannot be probed for existence — which is why
	 *         this is a 404 and not the 403 §2.4 gives elsewhere
	 */
	void markRead(String callerId, Role callerRole, String notificationId);

	/**
	 * Marks every unread notification for the caller read (§9.2's note) — the endpoint behind
	 * the "Mark all as read" control.
	 *
	 * <p>Never fails for having nothing to do. A caller with an empty bell, or one who cannot
	 * have notifications at all, succeeds without changing anything.
	 */
	void markAllRead(String callerId, Role callerRole);
}