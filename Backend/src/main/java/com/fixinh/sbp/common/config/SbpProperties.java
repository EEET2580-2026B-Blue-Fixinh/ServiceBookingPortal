package com.fixinh.sbp.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Typed binding for everything under {@code fixinh.sbp} in application.yml.
 *
 * <p>Only the sections the auth slice needs are bound today ({@code jwt}, {@code mongo},
 * {@code cors}). The remaining keys in application.yml ({@code smp}, {@code cloudinary},
 * {@code google-oauth}) stay unbound until the tasks that own them land.
 */
@ConfigurationProperties(prefix = "fixinh.sbp")
@Getter
@Setter
public class SbpProperties {

	private Jwt jwt = new Jwt();
	private Mongo mongo = new Mongo();
	private Cors cors = new Cors();
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
}