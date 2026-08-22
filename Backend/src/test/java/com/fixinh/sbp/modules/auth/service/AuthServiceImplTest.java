package com.fixinh.sbp.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.model.AuthProvider;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.auth.repository.UserCredentialRepository;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;

class AuthServiceImplTest {

	private static final String USER_ID = "user-1";

	private UserCredentialRepository credentials;
	private CustomerInternalService customers;
	private RegionResolver regionResolver;
	private GoogleOAuthService googleOAuth;
	private AuthServiceImpl service;

	@BeforeEach
	void setUp() {
		credentials = mock(UserCredentialRepository.class);
		customers = mock(CustomerInternalService.class);
		regionResolver = mock(RegionResolver.class);
		googleOAuth = mock(GoogleOAuthService.class);
		JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
		AccountProfileReader profileReader = mock(AccountProfileReader.class);
		when(tokenProvider.generate(any())).thenReturn("token");
		when(tokenProvider.getAccessTokenTtlSeconds()).thenReturn(3600L);
		when(profileReader.fullNameOf(any())).thenReturn("Test User");
		service = new AuthServiceImpl(
				mock(RegistrationValidator.class), credentials, customers, regionResolver,
				profileReader, mock(PasswordEncoder.class), tokenProvider, googleOAuth);
	}

	@Test
	void googleLoginCanLinkAnImmutablePasswordProviderSet() {
		UserCredential credential = credential(Region.SOUTH);
		credential.setAuthProviders(Set.of(AuthProvider.PASSWORD));
		when(googleOAuth.verifyCodeAndGetUser("code", "http://localhost/login"))
				.thenReturn(new GoogleOAuthService.GoogleUserInfo(
						"google-sub-1", "user@example.com", "Test User", null));
		when(credentials.findByEmail("user@example.com")).thenReturn(Optional.of(credential));

		service.loginWithGoogle(new GoogleLoginCommand("code", "http://localhost/login"));

		assertEquals(Set.of(AuthProvider.PASSWORD, AuthProvider.GOOGLE),
				credential.getAuthProviders());
		assertEquals("google-sub-1", credential.getAuthenticationIdentities().get(0)
				.getExternalAccountIdentifier());
		verify(credentials).save(credential);
	}


	private UserCredential credential(Region region) {
		return UserCredential.builder()
				.id(USER_ID).email("user@example.com")
				.accountRole(Role.CUSTOMER).accountStatus(AccountStatus.ACTIVE)
				.authProviders(Set.of(AuthProvider.GOOGLE)).homeRegion(region).build();
	}

}
