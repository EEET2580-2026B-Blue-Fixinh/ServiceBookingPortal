package com.fixinh.sbp.common.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.file.FileWritingEmailClient;
import com.fixinh.sbp.common.email.smtp.SmtpEmailClient;

/**
 * Which {@link EmailClient} each configuration produces — the two implementations and their
 * properties alone, with no database and no web layer.
 *
 * <p>Req 3.1.S5 requires that the {@code dev} profile sends nothing externally. The first test
 * reads the mode the real {@code dev} profile resolves to and wires with exactly that. The rest
 * pin what makes it safe: sending is opted into with {@code smtp}, never fallen into, and even
 * then refuses to start without a configured mail sender.
 */
class EmailClientWiringTest {

	private static final String FROM = "fixinh.sbp.email.from-address=no-reply@fixinh.vn";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfig.class, FileWritingEmailClient.class,
					SmtpEmailClient.class);

	@Test
	void theDevProfileWiresTheFileClientEvenWhenAMailSenderExists() throws IOException {
		String devMode = binderFor("application-dev.yml", "application.yml")
				.bind("fixinh.sbp.email.mode", String.class).get();
		assertEquals("file", devMode, "application-dev.yml pins the mode; "
				+ "a placeholder there would let EMAIL_MODE lift it");

		// A mail sender exists whenever spring.mail.host is set. File mode has to win anyway.
		runner.withPropertyValues("fixinh.sbp.email.mode=" + devMode, FROM)
				.withBean(JavaMailSender.class, JavaMailSenderImpl::new)
				.run(context -> {
					assertInstanceOf(FileWritingEmailClient.class,
							context.getBean(EmailClient.class));
					assertTrue(context.getBeansOfType(SmtpEmailClient.class).isEmpty(),
							"nothing able to send may exist on dev");
				});
	}

	@Test
	void anUnsetModeFallsIntoTheClientThatSendsNothing() {
		runner.run(context -> assertInstanceOf(FileWritingEmailClient.class,
				context.getBean(EmailClient.class)));
	}

	@Test
	void smtpModeWithoutAMailHostRefusesToStart() {
		// Failing at boot, naming the missing property, beats discovering it when an
		// administrator creates the first staff member.
		runner.withPropertyValues("fixinh.sbp.email.mode=smtp", FROM).run(context -> {
			Throwable failure = context.getStartupFailure();
			assertNotNull(failure, "smtp mode with no mail sender must not start");
			assertTrue(messagesOf(failure).contains("spring.mail.host"), messagesOf(failure));
		});
	}

	@Test
	void smtpModeWithAMailSenderWiresTheSmtpClient() {
		runner.withPropertyValues("fixinh.sbp.email.mode=smtp", FROM)
				.withBean(JavaMailSender.class, JavaMailSenderImpl::new)
				.run(context -> {
					assertInstanceOf(SmtpEmailClient.class, context.getBean(EmailClient.class));
					assertTrue(context.getBeansOfType(FileWritingEmailClient.class).isEmpty());
				});
	}

	/** Every message down the cause chain, so the check does not depend on wrapping depth. */
	private static String messagesOf(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			messages.append(cause.getMessage()).append('\n');
		}
		return messages.toString();
	}

	/** The real YAML files, highest precedence first, as Spring Boot consults them. */
	private static Binder binderFor(String... files) throws IOException {
		YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
		List<PropertySource<?>> sources = new ArrayList<>();
		for (String file : files) {
			sources.addAll(loader.load(file, new ClassPathResource(file)));
		}
		return new Binder(ConfigurationPropertySources.from(sources));
	}

	/** Binds {@code fixinh.sbp.*} the way {@code @ConfigurationPropertiesScan} does in the app. */
	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SbpProperties.class)
	static class PropertiesConfig {
	}
}