package com.fixinh.sbp.common.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Typed binding for everything under {@code fixinh.sbp} in application.yml.
 */
@ConfigurationProperties(prefix = "fixinh.sbp")
@Getter
@Setter
public class SbpProperties {

	private Jwt jwt = new Jwt();
	private Mongo mongo = new Mongo();
	private Cors cors = new Cors();
	private Smp smp = new Smp();
	private Email email = new Email();
	private OAuth oauth = new OAuth();

	/** Req 3.2.M1-M3: access tokens issued on sign-in and validated on every protected call. */
	@Getter
	@Setter
	public static class Jwt {
		private String secret;
		private String issuer;
		private long accessTokenTtlMinutes = 60;
	}

	/**
	 * Req 3.1.U1/U2: customers are sharded north/south by province, so there are two
	 * physically separate Mongo databases rather than one.
	 */
	@Getter
	@Setter
	public static class Mongo {
		private Shard south = new Shard();
		private Shard north = new Shard();

		/**
		 * Connections held open per shard. The driver defaults to 100, but a free Atlas cluster
		 * admits 500 in total — five developers running the app at once would sit exactly on the
		 * ceiling before Compass or a test run is counted.
		 */
		private int maxPoolSize = 20;

		/**
		 * How long to wait for a reachable server before failing. The driver defaults to 30
		 * seconds, which makes a missing IP-allowlist entry look like a hang rather than an error.
		 */
		private int serverSelectionTimeoutSeconds = 5;

		@Getter
		@Setter
		public static class Shard {
			private String uri;
		}
	}

	/** Req A.2.6: restrictive CORS. Origins are listed explicitly; wildcards are never used. */
	@Getter
	@Setter
	public static class Cors {
		private List<String> allowedOrigins = new ArrayList<>();
	}

	/** Req 3.2.U1-U3: Google OAuth 2.0 configuration. */
	@Getter
	@Setter
	public static class OAuth {
		private Google google = new Google();

		@Getter
		@Setter
		public static class Google {
			private String clientId;
			private String clientSecret;
			private String redirectUri;
		}
	}

	/**
	 * Req 4.S2/4.M1/4.M3: Booking/Technician/Staff data is normally owned by SMP. {@code mode}
	 * switches which {@code Smp*Client} beans are wired: "mock" serves local fixtures so SBP
	 * runs standalone, "live" calls the real SMP API once it exists.
	 */
	@Getter
	@Setter
	public static class Smp {
		private String mode = "mock";
		private String baseUrl;
		private String webhookSharedSecret;
	}

	/**
	 * Req 3.1.S5, API Doc §10.6: the welcome email a new staff member receives.
	 *
	 * <p><b>Policy only.</b> The SMTP transport — host, port, credential, TLS — is Spring
	 * Boot's own {@code spring.mail.*} namespace and is set by the {@code integration}
	 * profile alone. Splitting the two keeps the provider credential out of the file
	 * everyone reads, and leaves Boot's autoconfiguration owning what it already owns.
	 */
	@Getter
	@Setter
	public static class Email {

		/**
		 * Which {@code EmailClient} is active. Defaults to {@link Mode#FILE} so a missing or
		 * half-finished configuration cannot reach a real inbox — sending is opted into,
		 * never fallen into.
		 */
		private Mode mode = Mode.FILE;

		/** Envelope sender. Must be an address the provider has authorised, or mail is dropped. */
		private String fromAddress;

		/** Display name shown beside {@link #fromAddress}. */
		private String fromName;

		/**
		 * Where {@link Mode#FILE} writes each message. Under {@code target/} deliberately:
		 * {@code .gitignore} already covers it, and {@code mvn clean} removes messages that
		 * carry an initial password rather than leaving them in the working tree.
		 */
		private String outboxDirectory = "target/mail";

		/**
		 * The sign-in link printed in the welcome email.
		 */
		private String staffSignInUrl;

		/** Background redelivery of emails a provider refused (Req 3.1.S5). */
		private Retry retry = new Retry();

		/**
		 * What the retry sweeper is allowed to do.
		 *
		 * <p>Two neighbouring keys are read by annotations rather than bound here, because
		 * {@code @Scheduled} and {@code @ConditionalOnProperty} resolve placeholders before any
		 * binding happens and cannot see this object: {@code retry.enabled} and
		 * {@code retry.sweep-interval}. They are documented in application.yml beside these.
		 */
		@Getter
		@Setter
		public static class Retry {

			/**
			 * Attempts in total, the first one included. On the last failure the notification is
			 * marked {@code ABANDONED} rather than retried forever — a message nobody will ever
			 * receive should be a visible dead end, not a job that runs until the heat death of
			 * the cluster.
			 */
			private int maxAttempts = 5;

			/**
			 * The first wait after a failure. Each subsequent wait doubles, so the default gives
			 * attempts at roughly 0, 2, 6, 14 and 30 minutes — long enough to ride out a provider
			 * blip without hammering one that is genuinely down.
			 */
			private Duration baseDelay = Duration.ofMinutes(2);

			/** How many outstanding notifications one sweep will look at, per store. */
			private int batchSize = 50;
		}

		/** How the message leaves the application. */
		public enum Mode {
			/** Written to {@link Email#outboxDirectory}. Nothing leaves the machine. */
			FILE,
			/** Sent through the provider configured under {@code spring.mail.*}. */
			SMTP
		}
	}
}
