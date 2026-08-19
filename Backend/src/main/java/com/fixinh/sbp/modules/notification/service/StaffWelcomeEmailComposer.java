package com.fixinh.sbp.modules.notification.service;

import org.springframework.stereotype.Component;

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