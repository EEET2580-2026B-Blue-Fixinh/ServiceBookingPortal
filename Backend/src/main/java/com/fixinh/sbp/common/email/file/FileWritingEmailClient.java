package com.fixinh.sbp.common.email.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes each message to disk instead of sending it — the implementation behind Req 3.1.S5's
 * acceptance criterion that <i>"the dev profile sends nothing externally"</i>.
 *
 * <p>Chosen when {@code fixinh.sbp.email.mode} is {@code file}, and when the property is
 * absent entirely. Absence matters: a half-configured environment falls into the harmless
 * implementation rather than the one that reaches real inboxes.
 *
 * <p><b>Why a file and not a no-op.</b> A no-op is easier, but you cannot read a no-op. The
 * welcome email carries an initial password and a sign-in link, and the only way to check the
 * template renders correctly is to open the message. Writing {@code .eml} means Outlook,
 * Thunderbird and VS Code all open it as mail rather than as text.
 *
 * <p><b>The outbox is a test hook.</b> Point {@code fixinh.sbp.email.outbox-directory} at
 * something unwritable — a path where a file already sits, say — and every send raises
 * {@link EmailDeliveryException}. That is how the 502 {@code EMAIL_DELIVERY_FAILED} branch in
 * §10.6 gets exercised in development without a broken mail server.
 */
@Component
@ConditionalOnProperty(prefix = "fixinh.sbp.email", name = "mode", havingValue = "file",
		matchIfMissing = true)
@Slf4j
public class FileWritingEmailClient implements EmailClient {

	/** Colons are legal in a timestamp and illegal in a Windows filename, so this has none. */
	private static final DateTimeFormatter FILE_TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

	/** The shape RFC 5322 wants in a Date header, and what makes the file open as mail. */
	private static final DateTimeFormatter HEADER_DATE =
			DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ROOT);

	private final SbpProperties.Email properties;

	public FileWritingEmailClient(SbpProperties properties) {
		this.properties = properties.getEmail();
	}

	/**
	 * Announces the active mode at startup. Without this line, "which client am I running?"
	 * is only answerable by reading configuration across three files.
	 */
	@PostConstruct
	void announceMode() {
		log.info("Email delivery: FILE mode. Nothing is sent — messages are written to {}",
				Path.of(properties.getOutboxDirectory()).toAbsolutePath());
	}

	@Override
	public void send(EmailMessage message) {
		Path directory = Path.of(properties.getOutboxDirectory());
		Path file = directory.resolve(fileNameFor(message));

		try {
			// Created on send rather than at startup, so a bad outbox path surfaces as a
			// delivery failure the retry logic can handle, not as a refusal to boot.
			Files.createDirectories(directory);
			Files.writeString(file, render(message), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new EmailDeliveryException(
					"Could not write the message to " + file.toAbsolutePath(), ex);
		}

		// Recipient, subject and path only. The body carries the initial password, and the
		// one place it is allowed to exist is inside the file (Req 3.1.S5 acceptance:
		// no credential or password in the application logs).
		log.info("Email written for {} - \"{}\" - {}", message.to(), message.subject(), file);
	}

	/**
	 * Sortable by name, and safe on Windows: an address contains {@code @} and dots, so it is
	 * reduced to characters a filename can hold. Two messages to the same recipient inside one
	 * millisecond would overwrite, which is not worth guarding in a development outbox.
	 */
	private String fileNameFor(EmailMessage message) {
		String recipient = message.to().replaceAll("[^A-Za-z0-9.-]", "_");
		return "%s_%s.eml".formatted(ZonedDateTime.now().format(FILE_TIMESTAMP), recipient);
	}

	/**
	 * A minimal RFC 5322 message: headers, one blank line, body. The blank line is what
	 * separates the two and is not optional — without it a mail client reads the whole thing
	 * as headers and shows an empty message.
	 */
	private String render(EmailMessage message) {
		return """
				Date: %s
				From: %s <%s>
				To: %s
				Subject: %s
				MIME-Version: 1.0
				Content-Type: text/plain; charset=UTF-8

				%s"""
				.formatted(
						ZonedDateTime.now().format(HEADER_DATE),
						properties.getFromName(),
						properties.getFromAddress(),
						message.to(),
						message.subject(),
						message.body());
	}
}