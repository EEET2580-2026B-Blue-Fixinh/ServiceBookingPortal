package com.fixinh.sbp.common.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The SMTP transport settings as each profile actually resolves them — read from the real
 * YAML files with Spring Boot's own loader and binder, with no context started.
 *
 * <p>Guards two things an edit to those files could break without any other test noticing.
 * The socket timeouts live in {@code application.yml} and must reach every profile, including
 * {@code integration}, whose own {@code spring.mail.properties} map would hide them if the two
 * maps replaced each other instead of merging. And nothing a {@code dev} run reads may set
 * {@code spring.mail.host}: with no host Spring Boot creates no mail sender, which is half of
 * what keeps development away from real inboxes (Req 3.1.S5). The other half is the
 * {@code file} mode pin in {@code application-dev.yml}.
 */
class MailTransportPropertiesTest {

	private static final Bindable<Map<String, String>> STRING_MAP =
			Bindable.mapOf(String.class, String.class);

	@Test
	void everyProfileInheritsTheSocketTimeouts() throws IOException {
		Map<String, String> properties = mailProperties("application.yml");

		assertEquals("5000", properties.get("mail.smtp.connectiontimeout"));
		assertEquals("5000", properties.get("mail.smtp.timeout"));
		assertEquals("5000", properties.get("mail.smtp.writetimeout"));
	}

	@Test
	void theIntegrationProfileKeepsItsTransportSettingsAndGainsTheTimeouts() throws IOException {
		Map<String, String> properties =
				mailProperties("application-integration.yml", "application.yml");

		assertEquals("true", properties.get("mail.smtp.auth"));
		assertEquals("true", properties.get("mail.smtp.starttls.enable"));
		assertEquals("5000", properties.get("mail.smtp.connectiontimeout"),
				"lost if the profile's map replaced the base one instead of merging with it");
		assertEquals("5000", properties.get("mail.smtp.timeout"));
		assertEquals("5000", properties.get("mail.smtp.writetimeout"));
	}

	@Test
	void theDevProfileSetsNoMailHost() throws IOException {
		Binder binder = binderFor("application-dev.yml", "application.yml");

		assertFalse(binder.bind("spring.mail.host", String.class).isBound(),
				"a host here would create a mail sender on dev");
	}

	private Map<String, String> mailProperties(String... files) throws IOException {
		return binderFor(files).bind("spring.mail.properties", STRING_MAP).orElse(Map.of());
	}

	/**
	 * A binder over the given files, highest precedence first — the order Spring Boot itself
	 * consults them in, so a profile file listed before {@code application.yml} outranks it
	 * exactly as it does at runtime. Placeholders are left unresolved, which is fine: nothing
	 * bound here contains one.
	 */
	private Binder binderFor(String... files) throws IOException {
		YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
		List<PropertySource<?>> sources = new ArrayList<>();
		for (String file : files) {
			sources.addAll(loader.load(file, new ClassPathResource(file)));
		}
		return new Binder(ConfigurationPropertySources.from(sources));
	}
}