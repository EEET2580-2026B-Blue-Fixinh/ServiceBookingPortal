package com.fixinh.sbp.modules.integration.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.integration.model.EventDirection;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;

/**
 * Integration events across the north and south stores, shaped like the other sharded
 * repositories: two templates, one chosen by the row's own region, never inferred.
 */
@Repository
public class ShardedIntegrationEventRepository implements IntegrationEventRepository {

	private static final String FIELD_ID = "_id";
	private static final String FIELD_DIRECTION = "direction";

	private final MongoTemplate south;
	private final MongoTemplate north;

	public ShardedIntegrationEventRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south;
		this.north = north;
	}

	@Override
	public IntegrationEvent insert(IntegrationEvent event) {
		return templateFor(requireRegion(event)).insert(event);
	}

	@Override
	public IntegrationEvent save(IntegrationEvent event) {
		return templateFor(requireRegion(event)).save(event);
	}

	@Override
	public Optional<IntegrationEvent> findInbound(Region region, String eventId) {
		if (region == null) {
			throw new IllegalStateException(
					"No region was resolved, so there is no store to look the event up in.");
		}
		if (eventId == null || eventId.isBlank()) {
			return Optional.empty();
		}

		// The direction is part of the match even though inbound ids come from SMP and
		// outbound ones would be Mongo's: the collection is shared by design, and a query
		// that assumed the id spaces never overlap would be one nobody re-checked when the
		// outbox arrived.
		Query query = Query.query(Criteria.where(FIELD_ID).is(eventId)
				.and(FIELD_DIRECTION).is(EventDirection.IN));
		return Optional.ofNullable(templateFor(region).findOne(query, IntegrationEvent.class));
	}

	private static Region requireRegion(IntegrationEvent event) {
		Region home = event.getHomeRegion();
		if (home == null) {
			throw new IllegalStateException(
					"IntegrationEvent has no homeRegion, so there is no store to write it to. "
							+ "Resolve the customer's region before writing.");
		}
		return home;
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}
}
