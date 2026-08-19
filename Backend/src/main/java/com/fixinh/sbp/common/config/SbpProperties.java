package com.fixinh.sbp.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Typed binding for everything under {@code fixinh.sbp} in application.yml.
 *
 * <p>Bound today: {@code jwt}, {@code mongo}, {@code cors} and {@code email}. The remaining
 * keys in application.yml ({@code smp}, {@code cloudinary}, {@code google-oauth}) stay unbound
 * until the tasks that own them land.
 */
@ConfigurationProperties(prefix = "fixinh.sbp")
@Getter
@Setter
public class SbpProperties {

	private Jwt jwt = new Jwt();
	private Mongo mongo = new Mongo();
	private Cors cors = new Cors();
	private Email email = new Email();

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
		 *
		 * <p><b>This points at SMP, not at SBP.</b> A staff account is created in and owned
		 * by SMP (Milestone 1 collection index: the {@code staff} collection is SMP's), and
		 * a receptionist or technician cannot sign in to SBP at all — so sending them here
		 * would be sending them to a door their account does not open. The default is a
		 * placeholder in the same spirit as {@code fixinh.sbp.smp.base-url}, and must be
		 * replaced with the real address before any live send.
		 */
		private String staffSignInUrl;

		/** How the message leaves the application. */
		public enum Mode {
			/** Written to {@link Email#outboxDirectory}. Nothing leaves the machine. */
			FILE,
			/** Sent through the provider configured under {@code spring.mail.*}. */
			SMTP
		}
	}
}