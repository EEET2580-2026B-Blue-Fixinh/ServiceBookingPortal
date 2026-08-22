package com.fixinh.sbp.modules.reference.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.reference.model.RepairServiceType;

/**
 * Backs the repair-service catalogue with a single store (API Documentation §5.2).
 *
 * <p>Not a customer-owned collection, so there is no region to resolve. It is kept in the south
 * store only because that template is already {@code @Primary} for anything in this codebase
 * that does not need to pick a side (see {@code ShardMongoConfig}) — both regions query the same
 * store here, unlike {@code ShardedServiceBookingRepository}.
 */
@Repository
public class MongoRepairServiceTypeRepository implements RepairServiceTypeRepository {

	private final MongoTemplate template;

	public MongoRepairServiceTypeRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate template) {
		this.template = template;
	}

	@Override
	public RepairServiceType save(RepairServiceType service) {
		return template.save(service);
	}

	@Override
	public List<RepairServiceType> findAll() {
		return template.findAll(RepairServiceType.class);
	}

	@Override
	public Optional<RepairServiceType> findByServiceId(String serviceId) {
		Query query = Query.query(Criteria.where("serviceId").is(serviceId));
		return Optional.ofNullable(template.findOne(query, RepairServiceType.class));
	}
}
