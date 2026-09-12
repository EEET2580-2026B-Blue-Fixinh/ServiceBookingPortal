package com.fixinh.sbp.modules.notification.dto.internal;

/**
 * What the admin module hands the notification module once SMP confirms a staff account
 * (API Documentation §10.6).
 *
 * @param staffId        SMP's identifier, used as the notification's recipient and as the
 *                       Shard Directory subject
 * @param fullName       for the greeting
 * @param email          where the message goes, and the address the staff member signs in with
 * @param role           {@code RECEPTIONIST} or {@code TECHNICIAN}, printed in the message so
 *                       the recipient knows what account they have been given
 * @param initialPassword set by SMP, never by SBP. See the note on {@link #toString()}
 * @param branchProvinceCode the province of the branch this staff member works at — the one
 *                       region fact SBP has at creation time, and what
 *                       {@code NotificationRegionResolver} turns into a store
 */
public record StaffWelcomeEmailCommand(
		String staffId,
		String fullName,
		String email,
		String role,
		String initialPassword,
		String branchProvinceCode) {

	public StaffWelcomeEmailCommand {
		if (staffId == null || staffId.isBlank()) {
			throw new IllegalArgumentException("A welcome email needs the staff id SMP returned.");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("A welcome email needs an address to go to.");
		}
	}

	/**
	 * <b>Redacts the password.</b>
	 *
	 * <p>A record's generated {@code toString()} prints every component, so one careless
	 * {@code log.debug("creating {}", command)} would put a staff member's initial password in
	 * the application log — which Req 3.1.S5's acceptance criteria forbid outright. Overriding
	 * here makes that impossible rather than merely discouraged: there is no way to print this
	 * object and see the password, whatever a future caller does.
	 *
	 * <p>Lombok's {@code @ToString.Exclude} does not apply to records, so this is written out.
	 */
	@Override
	public String toString() {
		return "StaffWelcomeEmailCommand[staffId=%s, fullName=%s, email=%s, role=%s, "
				+ "initialPassword=<redacted>, branchProvinceCode=%s]"
						.formatted(staffId, fullName, email, role, branchProvinceCode);
	}
}