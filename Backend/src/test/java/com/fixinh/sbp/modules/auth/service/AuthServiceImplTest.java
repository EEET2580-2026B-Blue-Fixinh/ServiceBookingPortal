package com.fixinh.sbp.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.blacklist.TokenBlacklistRepository;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LogoutCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RefreshCommand;
import com.fixinh.sbp.modules.auth.model.AuthProvider;
import com.fixinh.sbp.modules.auth.model.RefreshTokenRecord;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.auth.repository.UserCredentialRepository;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;

class AuthServiceImplTest {

	private static final String USER_ID = "user-1";

	private UserCredentialRepository credentials;
	private CustomerInternalService customers;
	private RegionResolver regionResolver;
	private GoogleOAuthService googleOAuth;
	private PasswordEncoder passwordEncoder;
	private RefreshTokenService refreshTokenService;
	private TokenBlacklistRepository tokenBlacklistRepository;
	private AuthServiceImpl service;

	@BeforeEach
	void setUp() {
		credentials = mock(UserCredentialRepository.class);
		customers = mock(CustomerInternalService.class);
		regionResolver = mock(RegionResolver.class);
		googleOAuth = mock(GoogleOAuthService.class);
		passwordEncoder = mock(PasswordEncoder.class);
		refreshTokenService = mock(RefreshTokenService.class);
		tokenBlacklistRepository = mock(TokenBlacklistRepository.class);
		JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
		AccountProfileReader profileReader = mock(AccountProfileReader.class);
		when(tokenProvider.generate(any())).thenReturn("token");
		when(tokenProvider.getAccessTokenTtlSeconds()).thenReturn(3600L);
		when(profileReader.fullNameOf(any())).thenReturn("Test User");
		service = new AuthServiceImpl(
				mock(RegistrationValidator.class), credentials, customers, regionResolver,
				profileReader, passwordEncoder, tokenProvider, googleOAuth,
				refreshTokenService, tokenBlacklistRepository);
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

	@Test
	void googleLoginPrefersTheStableSubjectOverEmail() {
		UserCredential credential = credential(Region.SOUTH);
		when(googleOAuth.verifyCodeAndGetUser("code", "http://localhost/login"))
				.thenReturn(new GoogleOAuthService.GoogleUserInfo(
						"google-sub-1", "changed@example.com", "Test User", null));
		when(credentials.findByGoogleSubject("google-sub-1"))
				.thenReturn(Optional.of(credential));

		service.loginWithGoogle(new GoogleLoginCommand("code", "http://localhost/login"));

		verify(credentials, never()).findByEmail("changed@example.com");
	}

	@Test
	void loginIssuesAndPersistsARefreshTokenAlongsideTheAccessToken() {
		UserCredential credential = credential(Region.SOUTH);
		credential.setPasswordHash("hashed");
		when(credentials.findByEmail("user@example.com")).thenReturn(Optional.of(credential));
		when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
		when(refreshTokenService.issue(credential)).thenReturn("user-1.raw-refresh-token");

		var result = service.login(new LoginCommand("user@example.com", "secret"));

		assertEquals("user-1.raw-refresh-token", result.refreshToken());
		verify(refreshTokenService).issue(credential);
		verify(credentials).save(credential);
	}

	@Test
	void logoutBlacklistsTheAccessTokenAndClearsTheStoredRefreshToken() {
		UserCredential credential = credential(Region.SOUTH);
		credential.setRefreshToken(new RefreshTokenRecord("hash", Instant.now(), Instant.now().plusSeconds(60)));
		when(credentials.findById(USER_ID)).thenReturn(Optional.of(credential));
		Instant expiresAt = Instant.now().plusSeconds(120);

		service.logout(new LogoutCommand(USER_ID, "jti-1", expiresAt));

		verify(tokenBlacklistRepository).blacklist("jti-1", expiresAt);
		// refreshTokenService is mocked, so its revoke() does not itself mutate credential here —
		// this checks logout() actually calls it (and persists the result), not what revoke() does.
		verify(refreshTokenService).revoke(credential);
		verify(credentials).save(credential);
	}

	@Test
	void refreshDelegatesToRefreshTokenServiceAndReturnsTheRotatedToken() {
		UserCredential credential = credential(Region.SOUTH);
		when(refreshTokenService.verifyAndRotate("old-token"))
				.thenReturn(new RefreshTokenService.RotationResult(credential, "new-token"));

		var result = service.refresh(new RefreshCommand("old-token"));

		assertEquals("new-token", result.refreshToken());
		assertNotNull(result.accessToken());
	}

	private UserCredential credential(Region region) {
		return UserCredential.builder()
				.id(USER_ID).email("user@example.com")
				.accountRole(Role.CUSTOMER).accountStatus(AccountStatus.ACTIVE)
				.authProviders(Set.of(AuthProvider.GOOGLE)).homeRegion(region).build();
	}

}
