package com.fixinh.sbp.modules.notification.repository;

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
}