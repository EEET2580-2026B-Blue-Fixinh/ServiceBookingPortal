package com.fixinh.sbp.common.email.smtp;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends through the provider configured under {@code spring.mail.*} (Req 3.1.S5).
 *
 * <p>Active only when {@code fixinh.sbp.email.mode} is {@code smtp}, which means the
 * {@code smtp} profile is active: stacked on {@code dev} by name for a real send from a
 * development machine, or brought in by {@code integration} through its profile group. The
 * {@code dev} profile on its own is pinned to {@code file} and never reaches this class.
 *
 * <p>Everything provider-specific stops here. The Notification module sees
 * {@link EmailClient#send} and {@link EmailDeliveryException}, and never learns that Jakarta
 * Mail, an SMTP host, or a credential were involved.
 */
@Component
@ConditionalOnProperty(prefix = "fixinh.sbp.email", name = "mode", havingValue = "smtp")
@Slf4j
public class SmtpEmailClient implements EmailClient {

	/**
	 * Resolved lazily rather than injected directly. Spring Boot only creates a
	 * {@code JavaMailSender} when {@code spring.mail.host} is set, so a direct dependency
	 * would fail with "no qualifying bean of type JavaMailSender" — accurate and useless.
	 * Deferring lets {@link #verifyConfiguration()} name the property that is actually
	 * missing.
	 */
	private final ObjectProvider<JavaMailSender> mailSenderProvider;

	private final SbpProperties.Email properties;

	public SmtpEmailClient(ObjectProvider<JavaMailSender> mailSenderProvider,
			SbpProperties properties) {
		this.mailSenderProvider = mailSenderProvider;
		this.properties = properties.getEmail();
	}

	/**
	 * Fails startup rather than the first send. A misconfigured provider discovered when an
	 * administrator creates their first staff member is a bad time to find out; discovered
	 * when the application boots, it is a two-minute fix.
	 */
	@PostConstruct
	void verifyConfiguration() {
		if (mailSenderProvider.getIfAvailable() == null) {
			throw new IllegalStateException(
					"fixinh.sbp.email.mode is 'smtp' but no mail sender exists. Set "
							+ "spring.mail.host (env MAIL_HOST), or set the mode to 'file'.");
		}
		if (properties.getFromAddress() == null || properties.getFromAddress().isBlank()) {
			throw new IllegalStateException(
					"fixinh.sbp.email.from-address is required when the mode is 'smtp'.");
		}

		log.info("Email delivery: SMTP mode. Messages are sent for real, from {}",
				properties.getFromAddress());
	}

	@Override
	public void send(EmailMessage message) {
		JavaMailSender mailSender = mailSenderProvider.getObject();
		MimeMessage mime = mailSender.createMimeMessage();

		try {
			// UTF-8 stated at every step. Staff names are Vietnamese, and a subject or
			// display name left to the platform default charset arrives as question marks.
			MimeMessageHelper helper =
					new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());

			helper.setFrom(new InternetAddress(properties.getFromAddress(),
					properties.getFromName(), StandardCharsets.UTF_8.name()));
			helper.setTo(message.to());
			helper.setSubject(message.subject());
			helper.setText(message.body(), false);

			mailSender.send(mime);
		}
		catch (MessagingException | UnsupportedEncodingException | MailException ex) {
			// One exception type out, whatever went wrong in here. The caller's job is to
			// record the attempt and answer 502 (§10.6), not to interpret provider errors.
			throw new EmailDeliveryException(
					"The email provider did not accept the message for " + message.to(), ex);
		}

		// Recipient and subject only — never the body, which carries the initial password.
		log.info("Email sent to {} - \"{}\"", message.to(), message.subject());
	}
}