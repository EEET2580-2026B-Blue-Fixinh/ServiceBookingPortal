package com.fixinh.sbp.modules.customer.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.customer.model.Customer;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads and writes customers across the north and south stores.
 *
 * <p><b>Why every account is written twice.</b> Sign-in receives an identifier (email or phone)
 * and no province, so it cannot know which shard owns the account. Rather than fan every login
 * out to both stores, registration mirrors the sign-in fields into the other shard, making login
 * a single-shard read.
 *
 * <p>The replica carries only what sign-in needs; {@code address} is left null so the profile
 * stays owned by one region.
 */
@Repository
@Slf4j
public class ShardedCustomerRepository implements CustomerRepository {

	private final MongoTemplate south;
	private final MongoTemplate north;
	private final RegionResolver regionResolver;

	public ShardedCustomerRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north,
			RegionResolver regionResolver) {
		this.south = south;
		this.north = north;
		this.regionResolver = regionResolver;
	}

	@Override
	public Customer save(Customer customer) {
		Region home = customer.getHomeRegion() != null
				? customer.getHomeRegion()
				: regionResolver.resolve(customer.getAddress().getProvinceCode());
		customer.setHomeRegion(home);

		Customer saved = templateFor(home).save(customer);

		// Best-effort mirror. Two writes rather than a transaction: this runs on a single dev
		// mongod and a failed mirror costs a login lookup, not data. It must still be loud.
		try {
			templateFor(home.opposite()).save(replicaOf(saved));
		} catch (RuntimeException ex) {
			log.error("Customer {} saved to {} but the {} replica failed. Sign-in may not find "
					+ "this account until the replica is repaired.",
					saved.getId(), home, home.opposite(), ex);
		}

		return saved;
	}

	@Override
	public Optional<Customer> findByEmail(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		return findOne(Criteria.where("email").is(normalise(email)));
	}

	@Override
	public Optional<Customer> findByPhoneNumber(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			return Optional.empty();
		}
		return findOne(Criteria.where("phoneNumber").is(phoneNumber.trim()));
	}

	@Override
	public boolean existsByEmail(String email) {
		return findByEmail(email).isPresent();
	}

	@Override
	public boolean existsByPhoneNumber(String phoneNumber) {
		return findByPhoneNumber(phoneNumber).isPresent();
	}

	/**
	 * Looks up by id in both stores and prefers whichever copy carries the profile ({@code
	 * address != null}), since one of the two is always just the sign-in replica.
	 */
	@Override
	public Optional<Customer> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}

		Customer southMatch = south.findById(id, Customer.class);
		if (southMatch != null && southMatch.getAddress() != null) {
			return Optional.of(southMatch);
		}

		Customer northMatch = north.findById(id, Customer.class);
		if (northMatch != null && northMatch.getAddress() != null) {
			return Optional.of(northMatch);
		}

		return Optional.ofNullable(southMatch != null ? southMatch : northMatch);
	}

	/**
	 * Each store holds an authoritative copy for its own region plus a sign-in replica for the
	 * other, and both carry the same {@code homeRegion} value — so filtering each store to the
	 * documents whose {@code homeRegion} matches that store returns exactly one, full-profile row
	 * per customer system-wide.
	 */
	@Override
	public List<Customer> findAll() {
		List<Customer> all = new ArrayList<>();
		all.addAll(south.find(Query.query(Criteria.where("homeRegion").is(Region.SOUTH)), Customer.class));
		all.addAll(north.find(Query.query(Criteria.where("homeRegion").is(Region.NORTH)), Customer.class));
		return all;
	}

	/**
	 * Checks south, then north. Because every account is mirrored, whichever store answers first
	 * has the sign-in fields — but it may be the replica, so the returned {@code address} can be
	 * null. Callers that need the profile must re-read from {@code homeRegion}.
	 */
	private Optional<Customer> findOne(Criteria criteria) {
		Query query = Query.query(criteria);

		Customer found = south.findOne(query, Customer.class);
		if (found == null) {
			found = north.findOne(query, Customer.class);
		}
		return Optional.ofNullable(found);
	}

	/** The sign-in subset. Same _id as the authoritative document; address deliberately dropped. */
	private Customer replicaOf(Customer source) {
		return Customer.builder()
				.id(source.getId())
				.email(source.getEmail())
				.phoneNumber(source.getPhoneNumber())
				.passwordHash(source.getPasswordHash())
				.fullName(source.getFullName())
				.role(source.getRole())
				.accountStatus(source.getAccountStatus())
				.authProviders(source.getAuthProviders())
				.registeredAt(source.getRegisteredAt())
				.homeRegion(source.getHomeRegion())
				.address(null)
				.build();
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}

	private String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
