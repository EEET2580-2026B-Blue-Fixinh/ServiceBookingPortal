package com.fixinh.sbp.modules.notification.model;

/**
 * What a notification is about.
 */
public enum NotificationType {

	/** A receptionist agreed price and time (Req 3.3.U2). Pushed over the socket. */
	BOOKING_CONFIRMED,

	/** Technicians assigned and a time fixed (Req 3.3.U2). Pushed over the socket. */
	BOOKING_SCHEDULED,

	/**
	 * An administrator created a staff account (Req 3.1.S5). Email only, and never pushed:
	 * §9.3 states only the two booking types go over the socket, and a staff member has no
	 * SBP session to push to in any case.
	 */
	STAFF_ACCOUNT_CREATED
}