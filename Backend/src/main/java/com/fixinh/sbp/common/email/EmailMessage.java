package com.fixinh.sbp.common.email;

import java.util.Objects;

/**
 * One outbound message, in the only shape {@link EmailClient} accepts.
 *
 * <p><b>Deliberately carries no sender.</b> Who the mail is from is deployment configuration
 * ({@code fixinh.sbp.email.from-address}), not a decision a caller makes — so every message
 * leaves with the same identity and no caller can get it wrong.
 *
 * <p><b>Plain text only.</b> A welcome email carrying a sign-in link reads perfectly well as
 * text, and text cannot turn a staff member's name into markup in a rendered document. If a
 * styled template is wanted later it is one more field here and one branch in each client;
 * nothing that calls the interface has to change.
 *
 * @param to      recipient address
 * @param subject subject line
 * @param body    message body, plain UTF-8 text
 */
public record EmailMessage(String to, String subject, String body) {

	/**
	 * Validated here rather than in each client, so both implementations behave identically
	 * and a malformed message fails at the caller instead of somewhere inside a provider.
	 */
	public EmailMessage {
		if (to == null || to.isBlank()) {
			throw new IllegalArgumentException("Email recipient is required.");
		}
		if (subject == null || subject.isBlank()) {
			throw new IllegalArgumentException("Email subject is required.");
		}
		Objects.requireNonNull(body, "Email body must not be null.");
	}
}