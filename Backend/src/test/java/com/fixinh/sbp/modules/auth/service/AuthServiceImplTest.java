package com.fixinh.sbp.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.auth.dto.internal.CompleteProfileCommand;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.model.AuthProvider;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.auth.repository.UserCredentialRepository;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateAddressCommand;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;
import com.fixinh.sbp.modules.customer.model.Customer;

class AuthServiceImplTest {

	private static final String USER_ID = "user-1";

	private UserCredentialRepository credentials;
	private CustomerInternalService customers;
	private RegionResolver regionResolver;
	private GoogleOAuthService googleOAuth;
	private ShardDirectoryRepository shardDirectory;
	private AuthServiceImpl service;

	@BeforeEach
	void setUp() {
		credentials = mock(UserCredentialRepository.class);
		customers = mock(CustomerInternalService.class);
		regionResolver = mock(RegionResolver.class);
		googleOAuth = mock(GoogleOAuthService.class);
		shardDirectory = mock(ShardDirectoryRepository.class);
		JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
		AccountProfileReader profileReader = mock(AccountProfileReader.class);
		when(tokenProvider.generate(any())).thenReturn("token");
		when(tokenProvider.getAccessTokenTtlSeconds()).thenReturn(3600L);
		when(profileReader.fullNameOf(any())).thenReturn("Test User");
		service = new AuthServiceImpl(
				mock(RegistrationValidator.class), credentials, customers, regionResolver,
				profileReader, mock(PasswordEncoder.class), tokenProvider, googleOAuth,
				shardDirectory);
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
		assertEquals("google-sub-1", credential.getAuthenticationIdentities().getFirst()
				.getExternalAccountIdentifier());
		verify(credentials).save(credential);
	}

	@Test
	void profileCompletionMovesBothDocumentsAndUpdatesDirectory() {
		UserCredential credential = credential(Region.SOUTH);
		Customer customer = Customer.builder().id(USER_ID).homeRegion(Region.SOUTH).build();
		when(regionResolver.resolve("HN")).thenReturn(Region.NORTH);
		when(customers.findById(USER_ID)).thenReturn(Optional.of(customer));
		when(credentials.findById(USER_ID)).thenReturn(Optional.of(credential));
		when(customers.updateAddress(any(UpdateAddressCommand.class))).thenReturn(Region.NORTH);

		service.completeProfile(USER_ID, command());

		assertEquals(Region.NORTH, credential.getHomeRegion());
		verify(credentials).relocate(credential, Region.SOUTH);
		verify(shardDirectory).register(SubjectType.CUSTOMER, USER_ID, Region.NORTH);
	}

	@Test
	void credentialFailureRestoresTheCustomerLocation() {
		UserCredential credential = credential(Region.SOUTH);
		Customer customer = Customer.builder().id(USER_ID).homeRegion(Region.SOUTH).build();
		when(regionResolver.resolve("HN")).thenReturn(Region.NORTH);
		when(customers.findById(USER_ID)).thenReturn(Optional.of(customer));
		when(credentials.findById(USER_ID)).thenReturn(Optional.of(credential));
		when(customers.updateAddress(any(UpdateAddressCommand.class))).thenReturn(Region.NORTH);
		doThrow(new IllegalStateException("north unavailable"))
				.when(credentials).relocate(credential, Region.SOUTH);

		assertThrows(IllegalStateException.class,
				() -> service.completeProfile(USER_ID, command()));
		verify(customers).restoreLocation(USER_ID, null, Region.SOUTH);
	}

	private UserCredential credential(Region region) {
		return UserCredential.builder()
				.id(USER_ID).email("user@example.com")
				.accountRole(Role.CUSTOMER).accountStatus(AccountStatus.ACTIVE)
				.authProviders(Set.of(AuthProvider.GOOGLE)).homeRegion(region).build();
	}

	private CompleteProfileCommand command() {
		return new CompleteProfileCommand("1", "Main Street", "Ward 1", "HN");
	}
}
