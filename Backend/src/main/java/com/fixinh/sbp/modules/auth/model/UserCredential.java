package com.fixinh.sbp.modules.auth.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Everything signing in needs, for every kind of account.
 *
 * <p>Profiles are separate documents — a customer's address and an administrator's flags have
 * nothing to do with authentication, and keeping them out means /auth/login reads one collection
 * regardless of who is signing in. {@link #id} is shared with the owning profile document, which
 * is what makes the one-to-one exact: there is no join key to keep in step, and the id is also
 * the token's {@code sub} claim.
 *
 * <p><b>Partitioned by region, like the profile it identifies.</b> A credential is filed in the
 * store its owner's province resolves to and exists in that store only — a northern customer's
 * credential is in the north database and nowhere else. Nothing is duplicated across regions.
 *
 * <p>The cost lands at sign-in. An identifier is an email or a phone number and carries no
 * province, so the owning store is unknown until the account is found and a lookup may have to
 * ask both. It also means email uniqueness cannot be enforced by an index: the two stores' unique
 * indexes cannot see each other, so uniqueness rests on the check registration runs before
 * inserting. See UserCredentialRepository.
 */
@Document(collection = "credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredential {

	/** Shared with the profile document this identifies, and issued as the token's {@code sub}. */
	@Id
	private String id;

	/**
	 * Unique within its own store. System-wide uniqueness is the registration check's job — this
	 * index cannot see the other database, so it catches a duplicate only within one region.
	 */
	@Indexed(unique = true)
	private String email;

	// sparse=true allows multiple null values (Google OAuth users have no phone number)
	@Indexed(unique = true, sparse = true)
	private String phoneNumber;

	/**
	 * BCrypt hash, or null for an account that can only sign in through a federated provider.
	 * A null hash must never match — see the password check in AuthServiceImpl.
	 */
	private String passwordHash;

	/** Gates login: a DEACTIVATED account fails sign-in even with the right password. */
	private AccountStatus accountStatus;

	/** The discriminator. Says which profile collection holds the rest of this account. */
	private Role accountRole;

	/** How this account can sign in — PASSWORD, GOOGLE, or both (Appendix A). */
	@Builder.Default
	private Set<AuthProvider> authProviders = new HashSet<>();

	/** Stable external identities; unlike email, the provider account id does not change. */
	@Builder.Default
	private List<AuthenticationIdentity> authenticationIdentities = new ArrayList<>();

	private Instant registeredAt;

	/**
	 * Which store holds this credential, resolved from the owner's address province at
	 * registration. Stored rather than recomputed because the credential has no address of its
	 * own to derive it from — and because changing a customer's province has to move this
	 * document between stores, which is an insert and a delete, not an update in place.
	 *
	 * <p>Null for an account with no region, such as an administrator; those live in the south
	 * store by default.
	 */
	private Region homeRegion;

	/** The account's one active refresh token; null when signed out or never signed in. */
	private RefreshTokenRecord refreshToken;
}
