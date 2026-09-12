package com.fixinh.sbp.modules.notification.interfaces.internal;

import com.fixinh.sbp.modules.notification.dto.external.NotificationDto;
import com.fixinh.sbp.modules.notification.dto.internal.NotificationCreateCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;

/**
 * What other modules may ask of the notification module.
 */
public interface NotificationInternalService {

	/**
	 * Sends the welcome email of Req 3.1.S5 and records the attempt.
	 *
	 * <p><b>Does not throw when the provider refuses.</b> A failed send comes back as
	 * {@link StaffWelcomeEmailResult#sent()} being false, with the attempt recorded for the
	 * background sweeper. §10.6 turns that into 502 {@code EMAIL_DELIVERY_FAILED} without
	 * rolling anything back, because the staff account exists in SMP either way — undoing it
	 * would be a worse outcome than a late email.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 {@code PROVINCE_CODE_INVALID} if
	 *         the branch province cannot be turned into a store. This one does throw: without a
	 *         store there is nowhere to file the notification, so there is nothing to report.
	 */
	StaffWelcomeEmailResult sendStaffWelcomeEmail(StaffWelcomeEmailCommand command);

	/**
	 * Records an in-app notification and returns it.
	 * Records a notification, delivers it over the real-time channel when its type is one
	 * §9.3 pushes, and returns it.
	 *
	 * <p><b>Saved first, delivered second (§9).</b> The write is the notification; the push is
	 * a courtesy to a customer who happens to be connected, and it can neither fail the save
	 * nor fail the caller — a customer who is offline reads it from §9.1 on their next sign-in.
	 *
	 * <p><b>Once per event.</b> When the command names the SMP event it came from, a second
	 * call carrying the same event id returns the notification the first call produced and
	 * pushes nothing: a replay of §11.7 is answered, not repeated. A command that names no
	 * event is a new notification every time, because there is nothing to recognise a repeat
	 * by.
	 *
	 * @throws IllegalArgumentException if the command is incomplete
	 * @throws IllegalStateException if the recipient is a customer with no profile, so there is
	 *         no store to file this in
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 {@code PROVINCE_CODE_INVALID} when
	 *         the recipient is a staff member whose branch province cannot be turned into a store
	 */
	NotificationDto raise(NotificationCreateCommand command);
}