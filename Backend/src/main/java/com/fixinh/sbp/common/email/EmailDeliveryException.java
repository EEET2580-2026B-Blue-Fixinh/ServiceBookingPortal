package com.fixinh.sbp.common.email;

/**
 * The provider refused, timed out, or could not be reached — the message did not go out.
 *
 * <p><b>This is not a fatal error, and it is never a reason to roll anything back.</b> API
 * Documentation §10.6 is explicit: when the welcome email fails, the staff account still
 * exists in SMP. Only the mail failed. The request answers 502 {@code EMAIL_DELIVERY_FAILED}
 * and the send is retried in the background. Callers are expected to catch this, record the
 * attempt, and carry on.
 *
 * <p>Unchecked, matching {@code ApiException} in this codebase and Spring's own
 * {@code MailException}.
 */
public class EmailDeliveryException extends RuntimeException {

	public EmailDeliveryException(String message) {
		super(message);
	}

	public EmailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}