package com.fixinh.sbp.modules.notification.model;

/**
 * Who a notification is addressed to.
 */
public enum RecipientType {

	/** A customer of this portal. {@code recipientIdentifier} is their SBP customer id. */
	CUSTOMER,

	/**
	 * A receptionist or technician. {@code recipientIdentifier} is SMP's staff id, and
	 * <b>SBP holds no matching record</b> — the {@code staff} collection is SMP's
	 * (Milestone 1 collection index). Nothing in SBP can be joined to it, which is why
	 * {@code recipientAddress} is stored rather than looked up.
	 */
	STAFF
}