package com.fixinh.sbp.modules.notification.model;

/**
 * How far an outbound notification has got.
 */
public enum DeliveryStatus {

	/** Written, not yet attempted. An {@code IN_APP} notification never sits here. */
	PENDING,

	/**
	 * The provider accepted the message. <b>Not the same as delivered</b> — acceptance means
	 * queued, so this is the furthest we can honestly claim.
	 */
	SENT,

	/** An attempt failed. The sweeper will try again; {@code attemptCount} says how often. */
	FAILED,

	/**
	 * Attempts exhausted. Terminal, and the signal for a human to look: the staff account
	 * exists but its owner never received their sign-in details.
	 */
	ABANDONED
}