package com.fixinh.sbp.modules.notification.model;

/**
 * What a notification is about.
 *
 * <p>Each value also says whether it is <b>pushed live</b> — handed to the real-time channel
 * of §9.3 the moment it is saved. §9.3 names exactly two, and the notification service reads
 * the flag rather than the name so a third live type is one line here, not a new branch
 * there.
 */
public enum NotificationType {

	/** A receptionist agreed price and time (Req 3.3.U2). Pushed over the socket. */
	BOOKING_CONFIRMED(true),

	/** Technicians assigned and a time fixed (Req 3.3.U2). Pushed over the socket. */
	BOOKING_SCHEDULED(true),

	/**
	 * An administrator created a staff account (Req 3.1.S5). Email only, and never pushed:
	 * §9.3 states only the two booking types go over the socket, and a staff member has no
	 * SBP session to push to in any case.
	 */
	STAFF_ACCOUNT_CREATED(false);

	private final boolean pushedLive;

	NotificationType(boolean pushedLive) {
		this.pushedLive = pushedLive;
	}

	/** True for the types §9.3 delivers over the real-time channel as soon as they are saved. */
	public boolean isPushedLive() {
		return pushedLive;
	}
}