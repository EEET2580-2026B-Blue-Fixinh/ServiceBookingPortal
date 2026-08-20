package com.fixinh.sbp.modules.notification.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;

/**
 * Notifications across the north and south stores.
 */
@Repository
public class ShardedNotificationRepository implements NotificationRepository {

	private final MongoTemplate south;
	private final MongoTemplate north;

	public ShardedNotificationRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south;
		this.north = north;
	}

	@Override
	public Notification save(Notification notification) {
		Region home = notification.getHomeRegion();
		if (home == null) {
			throw new IllegalStateException(
					"Notification has no homeRegion, so there is no store to write it to. "
							+ "Resolve the recipient's region before saving.");
		}

		return templateFor(home).save(notification);
	}

	@Override
	public Optional<Notification> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}

		Query query = Query.query(Criteria.where("_id").is(id));

		// South first, then north -- the same order ShardedCustomerRepository uses. Neither
		// store is more likely, so consistency between the two classes is the tie-breaker.
		Notification found = south.findOne(query, Notification.class);
		if (found == null) {
			found = north.findOne(query, Notification.class);
		}
		return Optional.ofNullable(found);
	}

		@Override
	public List<Notification> findOutstandingEmails(int limit) {
		// Field order matches the {deliveryStatus, lastAttemptAt} index from patch 3, so this
		// reads an index range rather than scanning every notification ever sent.
		Query query = Query.query(
						Criteria.where("deliveryStatus")
								.in(DeliveryStatus.PENDING, DeliveryStatus.FAILED)
								.and("deliveryChannel").is(DeliveryChannel.EMAIL))
				.with(Sort.by(Sort.Direction.ASC, "lastAttemptAt"))
				.limit(limit);

		// Both stores, because a notification lives in exactly one of them and the sweeper has
		// no recipient in hand to work out which.
		List<Notification> outstanding = new ArrayList<>(south.find(query, Notification.class));
		outstanding.addAll(north.find(query, Notification.class));
		return outstanding;
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}
}