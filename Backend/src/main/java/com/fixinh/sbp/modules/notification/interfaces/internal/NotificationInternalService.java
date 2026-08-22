package com.fixinh.sbp.modules.notification.interfaces.internal;

import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.dto.external.NotificationDto;
import com.fixinh.sbp.modules.notification.dto.internal.NotificationCreateCommand;

/**
 * What other modules may ask of the notification module.
 */
public interface NotificationInternalService {

	/**
	 * Sends the welcome email and records the attempt 
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
	 * Records a notification and returns it
	 *
	 * @throws IllegalArgumentException if the command is incomplete
	 * @throws IllegalStateException if the recipient is a customer with no profile, so there is
	 *         no store to file this in
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 {@code PROVINCE_CODE_INVALID} when
	 *         the recipient is a staff member whose branch province cannot be turned into a store
	 */
	NotificationDto raise(NotificationCreateCommand command);
}
