package com.fixinh.sbp.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fixinh.sbp.common.config.SbpProperties;

/**
 * Builds the Cloudinary client, and only when it will actually be used
 * ({@code fixinh.sbp.images.mode=cloudinary}).
 *
 * <p>Conditional on purpose: the credential defaults to blank, so constructing this
 * unconditionally would create a client that authenticates against nothing and fails at the
 * first upload rather than at startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "fixinh.sbp.images", name = "mode", havingValue = "cloudinary")
public class CloudinaryConfig {

	/**
	 * @throws IllegalStateException if the account is not fully configured. Failing at startup is
	 *         the point: {@code cloudinary} mode was asked for deliberately, so a missing key is
	 *         a misconfiguration to fix now, not a 502 for a customer to discover later.
	 */
	@Bean
	public Cloudinary cloudinary(SbpProperties properties) {
		SbpProperties.Cloudinary account = properties.getCloudinary();

		if (isBlank(account.getCloudName()) || isBlank(account.getApiKey())
				|| isBlank(account.getApiSecret())) {
			throw new IllegalStateException(
					"fixinh.sbp.images.mode is cloudinary, but cloud-name, api-key or api-secret "
							+ "is missing. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and "
							+ "CLOUDINARY_API_SECRET, or leave the mode at local.");
		}

		return new Cloudinary(ObjectUtils.asMap(
				"cloud_name", account.getCloudName(),
				"api_key", account.getApiKey(),
				"api_secret", account.getApiSecret(),
				// Req A.2.5 applies to what SBP calls as much as to what it serves.
				"secure", true));
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
