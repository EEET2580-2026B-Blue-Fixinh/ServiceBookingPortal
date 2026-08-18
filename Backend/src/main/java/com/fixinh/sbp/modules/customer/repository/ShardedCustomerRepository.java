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

/**
 * Reads and writes customers across the north and south stores.
 *
 * <p><b>Each account lives in exactly one shard</b> — the one its address province resolves to
 * (Req 3.1.U1). Nothing is duplicated: the north store holds northern customers only and the
 * south store southern ones only. A record is owned by one region, which is the whole point of
 * partitioning by region.
 *
 * <p>The cost is paid at sign-in. Login receives an identifier — an email or phone number — and
 * no province, so the owning store is unknown until the account is found and a lookup may have to
 * ask both. Uniqueness checks pay the same price, which is why they all route through
 * {@link #findOne}.
 */
@Repository
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

		return templateFor(home).save(customer);
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

	/** Looks up by id in both stores, since the id alone does not say which shard owns it. */
	@Override
	public Optional<Customer> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}

		Customer found = south.findById(id, Customer.class);
		if (found == null) {
			found = north.findById(id, Customer.class);
		}
		return Optional.ofNullable(found);
	}

	/** Every customer system-wide — each store holds only its own region's accounts. */
	@Override
	public List<Customer> findAll() {
		List<Customer> all = new ArrayList<>();
		all.addAll(south.findAll(Customer.class));
		all.addAll(north.findAll(Customer.class));
		return all;
	}

	/**
	 * Checks south, then north, and stops at the first hit. Because an account exists in only one
	 * store, a northern customer costs two queries and a southern one costs a single query — the
	 * price of the identifier carrying no region. Whichever store answers holds the authoritative
	 * document, so the returned {@code address} is always populated.
	 */
	private Optional<Customer> findOne(Criteria criteria) {
		Query query = Query.query(criteria);

		Customer found = south.findOne(query, Customer.class);
		if (found == null) {
			found = north.findOne(query, Customer.class);
		}
		return Optional.ofNullable(found);
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}

	private String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
