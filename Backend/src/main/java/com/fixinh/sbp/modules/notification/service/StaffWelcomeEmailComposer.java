package com.fixinh.sbp.modules.notification.service;

import org.springframework.stereotype.Component;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;

/**
 * Turns a new staff account into the message that announces it.
 */
@Component
public class StaffWelcomeEmailComposer {

	private static final String SUBJECT = "Welcome to Fixinh";

	private final SbpProperties.Email properties;

	public StaffWelcomeEmailComposer(SbpProperties properties) {
		this.properties = properties.getEmail();
	}

	/**
	 * The full message, password included.
	 *
	 * <p>This is the one place a staff member's initial password is written into text. It goes
	 * straight to {@code EmailClient} and, until delivery succeeds, into the notification's
	 * {@code undeliveredBody} so a retry can resend it. It is never logged and never used as
	 * the notification's {@code message} — see {@link #summaryFor}.
	 */
	public EmailMessage compose(StaffWelcomeEmailCommand command) {
		return new EmailMessage(command.email(), SUBJECT, body(command));
	}

	public String summaryFor(StaffWelcomeEmailCommand command) {
		return "Welcome email for %s (%s) to %s"
				.formatted(command.fullName(), command.role(), command.email());
	}

	/**
	 * The subject a given kind of email carries.
	 *
	 * <p>Exists so the retry sweeper can rebuild a message from a stored body without the
	 * subject having to be stored beside it. A subject is a property of the kind of email, not
	 * of one instance of it, so deriving beats persisting.
	 *
	 * @throws IllegalArgumentException for a type that is never emailed. The sweeper filters to
	 *         the {@code EMAIL} channel, so reaching this means something upstream is wrong and
	 *         should say so rather than invent a subject line.
	 */
	public String subjectFor(NotificationType type) {
		return switch (type) {
			case STAFF_ACCOUNT_CREATED -> SUBJECT;
			case BOOKING_CONFIRMED, BOOKING_SCHEDULED -> throw new IllegalArgumentException(
					type + " is an in-app notification and is never sent by email.");
		};
	}

	private String body(StaffWelcomeEmailCommand command) {
		return """
				Hello %s,

				An administrator has created a Fixinh staff account for you.

				  Role:      %s
				  Sign in:   %s
				  Password:  %s

				Sign in at %s and change your password once you are in.

				This password is temporary and was generated for you. If you were not
				expecting this message, tell your administrator before using it.

				Fixinh"""
				.formatted(
						command.fullName(),
						command.role(),
						command.email(),
						command.initialPassword(),
						properties.getStaffSignInUrl());
	}
}