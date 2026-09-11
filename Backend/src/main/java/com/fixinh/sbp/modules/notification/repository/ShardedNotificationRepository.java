package com.fixinh.sbp.modules.notification.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.RecipientType;

import lombok.extern.slf4j.Slf4j;

/**
 * Notifications across the north and south stores.
 */
@Repository
@Slf4j
public class ShardedNotificationRepository implements NotificationRepository {

	private static final String FIELD_ID = "_id";
	private static final String FIELD_RECIPIENT_TYPE = "recipientType";
	private static final String FIELD_RECIPIENT_IDENTIFIER = "recipientIdentifier";
	private static final String FIELD_READ_STATUS = "readStatus";
	private static final String FIELD_CREATED_AT = "createdAt";

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

		Query query = Query.query(Criteria.where(FIELD_ID).is(id));

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
		// Field order matches the {deliveryStatus, lastAttemptAt} index that
		// ShardIndexInitializer creates, so this reads an index range rather than scanning
		// every notification ever sent.
		Query query = Query.query(
						Criteria.where("deliveryStatus")
								.in(DeliveryStatus.PENDING, DeliveryStatus.FAILED)
								.and("deliveryChannel").is(DeliveryChannel.EMAIL))
				.with(Sort.by(Sort.Direction.ASC, "lastAttemptAt"))
				.limit(limit);

		// Both stores, because a notification lives in exactly one of them and the sweeper has
		// no recipient in hand to work out which. Asked one at a time, so a store that is down
		// costs only its own rows: the other store's emails are still owed, and still retried.
		List<Notification> outstanding = new ArrayList<>();
		boolean southAnswered = readOutstandingInto(outstanding, south, Region.SOUTH, query);
		boolean northAnswered = readOutstandingInto(outstanding, north, Region.NORTH, query);

		if (!southAnswered && !northAnswered) {
			throw new IllegalStateException("Neither store answered the outstanding-email "
					+ "query, so nothing can be retried on this sweep. Both failures are logged "
					+ "above.");
		}
		return outstanding;
	}

	/**
	 * Adds one store's outstanding emails to {@code into}, and says whether the store answered.
	 *
	 * <p>A failure is logged and swallowed here rather than thrown, the same way
	 * {@code ReplicatedShardDirectoryRepository} treats one unreachable copy: an unreachable
	 * store degrades its own region and nothing else. The sweeper cannot make that call itself
	 * — it never learns which store a row came from — so the repository that does know makes it.
	 */
	private boolean readOutstandingInto(List<Notification> into, MongoTemplate store,
			Region region, Query query) {

		try {
			into.addAll(store.find(query, Notification.class));
			return true;
		}
		catch (RuntimeException ex) {
			log.warn("The {} store did not answer the outstanding-email query; its emails wait "
					+ "for the next sweep", region, ex);
			return false;
		}
	}

	@Override
	public List<Notification> findForRecipient(Region region, RecipientType recipientType,
			String recipientIdentifier, boolean unreadOnly, int page, int size) {

		requireRecipient(region, recipientType, recipientIdentifier);

		Query query = recipientQuery(recipientType, recipientIdentifier, unreadOnly)
				// createdAt alone is not a total order: BSON keeps an Instant to the
				// millisecond, so tied documents could drift between pages under offset
				// paging and a reader would see one twice or miss it. _id is unique, and
				// both notification indexes carry it, so this stays index-served.
				.with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT, FIELD_ID))
				.skip((long) page * size)
				.limit(size);

		return templateFor(region).find(query, Notification.class);
	}

	@Override
	public long count(Region region, RecipientType recipientType, String recipientIdentifier,
			boolean unreadOnly) {

		requireRecipient(region, recipientType, recipientIdentifier);

		return templateFor(region).count(
				recipientQuery(recipientType, recipientIdentifier, unreadOnly),
				Notification.class);
	}

	@Override
	public boolean markRead(Region region, RecipientType recipientType,
			String recipientIdentifier, String notificationId) {

		requireRecipient(region, recipientType, recipientIdentifier);
		if (notificationId == null || notificationId.isBlank()) {
			return false;
		}

		// The recipient is part of the match, so somebody else's notification is simply not
		// found -- §9.2's 404 falls out of the query rather than being decided afterwards.
		Query query = Query.query(Criteria.where(FIELD_ID).is(notificationId)
				.and(FIELD_RECIPIENT_TYPE).is(recipientType)
				.and(FIELD_RECIPIENT_IDENTIFIER).is(recipientIdentifier));

		var result = templateFor(region)
				.updateFirst(query, Update.update(FIELD_READ_STATUS, true), Notification.class);

		// getMatchedCount, NOT getModifiedCount. Marking an already-read notification changes
		// nothing, so modified would be 0 -- and §9.2 requires that to be a success. Matched
		// answers the question actually being asked: does this notification belong to you.
		return result.getMatchedCount() > 0;
	}

	@Override
	public long markAllRead(Region region, RecipientType recipientType,
			String recipientIdentifier) {

		requireRecipient(region, recipientType, recipientIdentifier);

		// One updateMulti rather than a read followed by a loop of writes: the set of unread
		// notifications cannot change underneath us between the two halves of an operation
		// that does not have two halves.
		Query query = recipientQuery(recipientType, recipientIdentifier, true);

		return templateFor(region)
				.updateMulti(query, Update.update(FIELD_READ_STATUS, true), Notification.class)
				.getModifiedCount();
	}

	/**
	 * The predicate every recipient-scoped operation shares.
	 */
	private Query recipientQuery(RecipientType recipientType, String recipientIdentifier,
			boolean unreadOnly) {

		Criteria criteria = Criteria.where(FIELD_RECIPIENT_IDENTIFIER).is(recipientIdentifier)
				.and(FIELD_RECIPIENT_TYPE).is(recipientType);

		if (unreadOnly) {
			criteria = criteria.and(FIELD_READ_STATUS).is(false);
		}

		return Query.query(criteria);
	}

	/**
	 * <b>Refuses rather than defaults.</b> Falling through to a default store would answer
	 * with an empty list, which reads exactly like a recipient with no notifications — a
	 * silent wrong answer, where an exception is at least a loud one.
	 */
	private void requireRecipient(Region region, RecipientType recipientType,
			String recipientIdentifier) {

		if (region == null) {
			throw new IllegalStateException(
					"No region was resolved, so there is no store to read notifications from.");
		}
		if (recipientType == null || recipientIdentifier == null
				|| recipientIdentifier.isBlank()) {
			throw new IllegalArgumentException(
					"A notification lookup needs a recipient type and identifier.");
		}
	}

	private MongoTemplate templateFor(Region region) {
		return region == Region.SOUTH ? south : north;
	}
}