package com.fixinh.sbp.modules.admin.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.admin.model.Administrator;

/**
 * Administrator profiles, in the south store alongside the credentials.
 *
 * <p>An administrator belongs to no region, so no province decides where the record goes. It
 * lives beside the credential that identifies it, which means resolving an administrator after
 * sign-in touches the store that already answered.
 *
 * <p>Lookups are by id: sign-in resolves the credential first, and the profile shares that id.
 */
@Repository
public class AdministratorRepository {

	private final MongoTemplate south;

	public AdministratorRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south) {
		this.south = south;
	}

	public Administrator save(Administrator administrator) {
		return south.save(administrator);
	}

	public Optional<Administrator> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(
				south.findOne(Query.query(Criteria.where("_id").is(id)), Administrator.class));
	}
}
