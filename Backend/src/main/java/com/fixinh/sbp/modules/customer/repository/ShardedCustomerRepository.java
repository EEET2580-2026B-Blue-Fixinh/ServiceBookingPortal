package com.fixinh.sbp.modules.customer.repository;

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
 * Customer profiles across the north and south stores.
 *
 * <p><b>Each profile lives in exactly one shard</b> — the one its address province resolves to
 * (Req 3.1.U1). Nothing is duplicated: the north store holds northern customers only and the
 * south store southern ones only.
 *
 * <p>A profile is fetched by id, which the caller already has from their token, and the id
 * carries no region — so a read may have to ask both stores. That is cheaper than it was for
 * sign-in, because it happens after authentication rather than on every login attempt.
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
	public Optional<Customer> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}

		Query query = Query.query(Criteria.where("_id").is(id));

		Customer found = south.findOne(query, Customer.class);
		if (found == null) {
			found = north.findOne(query, Customer.class);
		}
		return Optional.ofNullable(found);
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}
}
