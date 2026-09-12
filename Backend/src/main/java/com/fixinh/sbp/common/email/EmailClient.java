package com.fixinh.sbp.common.email;

/**
 * Sends one email on SBP's behalf — today, the staff welcome email of Req 3.1.S5.
 *
 * <p>Two implementations, chosen by {@code fixinh.sbp.email.mode} and never by the caller:
 * <ul>
 *   <li>{@code file} — writes the message to disk and sends nothing. The default, and what the
 *       {@code dev} profile is pinned to, so development cannot reach a real inbox.</li>
 *   <li>{@code smtp} — sends through the provider configured under {@code spring.mail.*}.</li>
 * </ul>
 *
 * <p>The Notification module depends on this interface alone. Changing provider, or adding a
 * third implementation, touches nothing above it.
 */
public interface EmailClient {

	/**
	 * Sends one message, blocking until the provider accepts or refuses it.
	 *
	 * <p><b>Acceptance is not delivery.</b> A provider that returns success has queued the
	 * message, not put it in front of anybody. That is why the {@code Notification} record
	 * tracks {@code deliveryStatus} separately rather than treating a clean return as the end
	 * of the story.
	 *
	 * @throws EmailDeliveryException if the provider refused, timed out, or was unreachable.
	 *         Callers must handle it: the staff account still exists, so the right answer is
	 *         502 and a retry, never a rollback (§10.6).
	 */
	void send(EmailMessage message);
}