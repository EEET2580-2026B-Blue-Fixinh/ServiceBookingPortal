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
}
