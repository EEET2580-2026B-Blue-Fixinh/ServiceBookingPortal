package com.fixinh.sbp.modules.notification.model;

/**
 * How a notification reaches its recipient.
 */
public enum DeliveryChannel {

	/**
	 * Stored for the bell menu and, for the two booking types, pushed over the socket (§9.3).
	 * Delivery is complete once the document is written, so these are never retried.
	 */
	IN_APP,

	/**
	 * Handed to an email provider through {@code EmailClient}. The only channel with a failure
	 * mode worth retrying, and so the only one the retry sweeper looks at.
	 */
	EMAIL
}