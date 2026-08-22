package com.fixinh.sbp.modules.auth.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.auth.model.UserCredential;

/**
 * Credentials across the north and south stores.
 *
 * <p><b>Each credential lives in exactly one store</b> — the one its owner's province resolves
 * to, the same store as their profile. Nothing is duplicated across regions.
 *
 * <p>The cost is paid at sign-in. An identifier carries no province, so the owning store is
 * unknown until the account is found: south is asked first, then north. A southern account costs
 * one query and a northern one two.
 *
 * <p><b>Uniqueness is not index-enforced system-wide.</b> Each store has its own unique index on
 * email and phone, and they cannot see each other — so an index catches a duplicate only within
 * one region. Registration's {@code existsBy} check before insert is what covers the rest, and
 * it is a check-then-insert: two simultaneous registrations of the same email routing to
 * different stores could both pass it. Rare enough to accept here, and the fix if it ever
 * matters is a single directory of identifiers rather than a second index.
 */
@Repository
public class UserCredentialRepository {

	private final MongoTemplate south;
	private final MongoTemplate north;

	public UserCredentialRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south;
		this.north = north;
	}

	/**
	 * Writes to the store named by {@code homeRegion}. An account with no region — an
	 * administrator — has none set and goes south, which is where its profile lives too.
	 */
	public UserCredential save(UserCredential credential) {
		return templateFor(credential.getHomeRegion()).save(credential);
	}

	/** Writes the credential to its new shard, then removes the copy from the previous shard. */
	public UserCredential relocate(UserCredential credential, Region previousRegion) {
		Region targetRegion = credential.getHomeRegion();
		if (previousRegion == targetRegion) {
			return save(credential);
		}

		MongoTemplate target = templateFor(targetRegion);
		UserCredential saved = target.save(credential);
		try {
			templateFor(previousRegion).remove(
					Query.query(Criteria.where("_id").is(credential.getId())), UserCredential.class);
			return saved;
		}
		catch (RuntimeException ex) {
			target.remove(
					Query.query(Criteria.where("_id").is(credential.getId())), UserCredential.class);
			throw ex;
		}
	}

	public Optional<UserCredential> findByEmail(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		return findOne(Criteria.where("email").is(normalise(email)));
	}

	public Optional<UserCredential> findByPhoneNumber(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			return Optional.empty();
		}
		return findOne(Criteria.where("phoneNumber").is(phoneNumber.trim()));
	}

	public Optional<UserCredential> findByGoogleSubject(String subject) {
		if (subject == null || subject.isBlank()) {
			return Optional.empty();
		}
		Criteria identity = Criteria.where("authenticationIdentities").elemMatch(
				Criteria.where("provider").is("GOOGLE")
						.and("externalAccountIdentifier").is(subject));
		return findOne(identity);
	}

	public boolean existsByEmail(String email) {
		return findByEmail(email).isPresent();
	}

	public boolean existsByPhoneNumber(String phoneNumber) {
		return findByPhoneNumber(phoneNumber).isPresent();
	}

	/** Checks south, then north, since the id alone does not say which store owns it. */
	public Optional<UserCredential> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return findOne(Criteria.where("_id").is(id));
	}

	/** Every credential with this discriminator, across both stores. */
	public List<UserCredential> findByAccountRole(Role role) {
		Query query = Query.query(Criteria.where("accountRole").is(role));
		List<UserCredential> all = new ArrayList<>();
		all.addAll(south.find(query, UserCredential.class));
		all.addAll(north.find(query, UserCredential.class));
		return all;
	}

	/**
	 * Deletes a credential from the appropriate store.
	 *
	 * <p>Used for rollback when profile creation fails during Google OAuth sign-in.
	 */
	public void delete(UserCredential credential) {
		templateFor(credential.getHomeRegion()).remove(credential);
	}

	/**
	 * Checks south, then north, and stops at the first hit. Because a credential exists in only
	 * one store, whichever answers holds the authoritative document.
	 */
	private Optional<UserCredential> findOne(Criteria criteria) {
		Query query = Query.query(criteria);

		UserCredential found = south.findOne(query, UserCredential.class);
		if (found == null) {
			found = north.findOne(query, UserCredential.class);
		}
		return Optional.ofNullable(found);
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.NORTH ? north : south;
	}

	/** Emails are stored lower-cased, so lookups match without needing a collation. */
	private String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
