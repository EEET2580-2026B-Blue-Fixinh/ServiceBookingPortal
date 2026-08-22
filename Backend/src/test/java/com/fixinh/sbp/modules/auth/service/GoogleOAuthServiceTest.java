package com.fixinh.sbp.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;

class GoogleOAuthServiceTest {
	@Test
	void rejectsARedirectUriOutsideTheConfiguredAllowlistBeforeCallingGoogle() {
		SbpProperties properties = new SbpProperties();
		properties.getOauth().getGoogle().setRedirectUri("http://localhost:3000/login");
		GoogleOAuthService service = new GoogleOAuthService(properties);

		ApiException error = assertThrows(ApiException.class,
				() -> service.verifyCodeAndGetUser("secret-code", "https://attacker.test/login"));

		assertEquals(ErrorCode.OAUTH_CODE_INVALID, error.getErrorCode());
	}
}
