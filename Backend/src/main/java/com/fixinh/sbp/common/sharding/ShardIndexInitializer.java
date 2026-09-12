package com.fixinh.sbp.common.sharding;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.security.blacklist.BlacklistedToken;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.admin.model.Administrator;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.rating.model.Rating;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the unique indexes both regional stores need (Req 3.1.S2), after the
 * application is up rather than while its beans are being built.
 */
@Component
@Slf4j
public class ShardIndexInitializer {

    private static final String EMAIL = "email";
    private static final String PHONE_NUMBER = "phoneNumber";

    private final MongoTemplate south;
    private final MongoTemplate north;

    public ShardIndexInitializer(
            @Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
            @Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
        this.south = south;
        this.north = north;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        ensureCustomerIndexes(south, Region.SOUTH);
        ensureCustomerIndexes(north, Region.NORTH);
        ensureCredentialIndexes(south, Region.SOUTH);
        ensureCredentialIndexes(north, Region.NORTH);
        ensureNotificationIndexes(south, Region.SOUTH);
        ensureNotificationIndexes(north, Region.NORTH);
        ensureIntegrationEventIndexes(south, Region.SOUTH);
        ensureIntegrationEventIndexes(north, Region.NORTH);
        ensureBookingIndexes(south, Region.SOUTH);
        ensureBookingIndexes(north, Region.NORTH);
        ensureRatingIndexes(south, Region.SOUTH);
        ensureRatingIndexes(north, Region.NORTH);
        ensureAdministratorIndexes(south);
        ensureBlacklistIndexes(south, Region.SOUTH);
        ensureBlacklistIndexes(north, Region.NORTH);
    }

    /**
     * TTL index for revoked access tokens: a document is removed at (or shortly after — Mongo's
     * TTL monitor runs roughly every 60s) {@code expiresAt}, which is the original access
     * token's own expiry. This is the whole mechanism that makes the blacklist self-cleaning —
     * without it, revoked tokens would accumulate forever instead of expiring.
     */
    private void ensureBlacklistIndexes(MongoTemplate template, Region region) {
        try {
            template.indexOps(BlacklistedToken.class)
                    .createIndex(new Index().on("expiresAt", Sort.Direction.ASC)
                            .expire(java.time.Duration.ZERO));
            log.info("Blacklist TTL index ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure blacklist TTL index on {} shard — revoked tokens will "
                    + "accumulate instead of expiring until this succeeds", region, ex);
        }
    }

    private void ensureBookingIndexes(MongoTemplate template, Region region) {
        try {
            IndexOperations indexes = template.indexOps(ServiceBooking.class);
            indexes.createIndex(new Index().on("bookingRef", Sort.Direction.ASC).unique());
            indexes.createIndex(new Index().on("customerId", Sort.Direction.ASC));
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure booking indexes on {} shard", region, ex);
        }
    }

    private void ensureRatingIndexes(MongoTemplate template, Region region) {
        try {
            IndexOperations indexes = template.indexOps(Rating.class);
            indexes.createIndex(new Index().on("bookingRef", Sort.Direction.ASC).unique()
                    .partial(PartialIndexFilter.of(Criteria.where("deletedAt").is(null)))
                    .named("one_live_rating_per_booking"));
            indexes.createIndex(new Index().on("customerId", Sort.Direction.ASC)
                    .on("submittedAt", Sort.Direction.DESC));
            indexes.createIndex(new Index().on("serviceId", Sort.Direction.ASC)
                    .on("submittedAt", Sort.Direction.DESC));
            indexes.createIndex(new Index().on("technicians.technicianId", Sort.Direction.ASC)
                    .on("submittedAt", Sort.Direction.DESC));
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure rating indexes on {} shard", region, ex);
        }
    }

    /**
     * The administrators' email index, created only when no index already covers that field.
     *
     * <p>Matched on fields, never on name, for the reason {@link #ensureCustomerIndexes} gives.
     * {@code Administrator#email} carries {@code @Indexed}, which names its index {@code email};
     * {@code Index().on(EMAIL)} asks for {@code email_1} over the same field, and Mongo refuses a
     * second index on one field whatever it is called. That refusal — error 85,
     * {@code IndexOptionsConflict} — was the warning and stack trace printed on every startup.
     */
    private void ensureAdministratorIndexes(MongoTemplate template) {
        try {
            IndexOperations indexes = template.indexOps(Administrator.class);
            boolean alreadyCovered = indexes.getIndexInfo().stream()
                    .anyMatch(existing -> coversExactly(existing, List.of(EMAIL)));
            if (!alreadyCovered) {
                indexes.createIndex(new Index().on(EMAIL, Sort.Direction.ASC));
            }
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure administrator indexes", ex);
        }
    }

    private void ensureCredentialIndexes(MongoTemplate template, Region region) {
        try {
            IndexOperations indexes = template.indexOps(UserCredential.class);
            boolean emailCorrect = false;
            boolean phoneCorrect = false;

            for (IndexInfo existing : indexes.getIndexInfo()) {
                if (coversExactly(existing, List.of(EMAIL))) {
                    if (existing.isUnique()) {
                        emailCorrect = true;
                    }
                    else {
                        indexes.dropIndex(existing.getName());
                    }
                }
                else if (coversExactly(existing, List.of(PHONE_NUMBER))) {
                    if (existing.isUnique() && existing.isSparse()) {
                        phoneCorrect = true;
                    }
                    else {
                        indexes.dropIndex(existing.getName());
                        log.info("Dropped non-sparse credential phone index {} on {} shard",
                                existing.getName(), region);
                    }
                }
            }

            if (!emailCorrect) {
                indexes.createIndex(new Index().on(EMAIL, Sort.Direction.ASC).unique());
            }
            if (!phoneCorrect) {
                indexes.createIndex(new Index().on(PHONE_NUMBER, Sort.Direction.ASC)
                        .unique().sparse());
            }
            indexes.createIndex(new Index()
                    .on("authenticationIdentities.externalAccountIdentifier", Sort.Direction.ASC));
            log.info("Credential indexes ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure credential indexes on {} shard", region, ex);
        }
    }

    /**
     * Customer indexes.
     *
     * <p><b>Nothing here is unique, which is a correction.</b> An earlier version created two
     * indexes that were, against a Customer that has since changed shape:
     *
     * <ul>
     *   <li>{@code phoneNumber} no longer exists on Customer — it moved to UserCredential.
     *       Every customer document therefore had none, and a unique index over a field
     *       nobody has collides on null as soon as two documents exist:
     *       {@code E11000 ... dup key: { phoneNumber: null }}. That threw partway through, so
     *       a shard holding two or more customers ended up with no customer indexes at
     *       all.</li>
     *   <li>{@code email} is still a field, but a denormalised copy of the credential's, kept
     *       so listings need no join. Customer#email says outright that uniqueness is the
     *       credential's to enforce and that a second unique index over a copy "would fail
     *       confusingly rather than usefully" — which is what it then did.</li>
     * </ul>
     *
     * <p>Failures are logged, not thrown: an unreachable shard should degrade that region,
     * not stop the application serving the other one.
     */
    private void ensureCustomerIndexes(MongoTemplate template, Region region) {
        try {
            IndexOperations indexes = template.indexOps(Customer.class);
            boolean emailIndexAlreadyCorrect = false;

            // Matched on the fields an index covers, never on its name. Names vary with how
            // an index was made -- @Indexed produces "email", Index().on("email") produces
            // "email_1", and one made by hand in Atlas could be called anything. Mongo, though,
            // rejects a second index over the same fields whatever it is called, so the fields
            // are the only thing worth comparing.
            for (IndexInfo existing : indexes.getIndexInfo()) {
                if (coversExactly(existing, List.of(PHONE_NUMBER))) {
                    indexes.dropIndex(existing.getName());
                    log.info("Dropped customer index {} on {} shard: phoneNumber is no longer "
                            + "a field on Customer", existing.getName(), region);
                }
                else if (coversExactly(existing, List.of(EMAIL))) {
                    if (existing.isUnique()) {
                        indexes.dropIndex(existing.getName());
                        log.info("Dropped unique customer index {} on {} shard: an email's "
                                + "uniqueness belongs to the credential, not to this copy",
                                existing.getName(), region);
                    }
                    else {
                        // Already what we want. Leaving it alone whatever it is called avoids
                        // dropping and rebuilding a correct index on every startup.
                        emailIndexAlreadyCorrect = true;
                    }
                }
            }

            if (!emailIndexAlreadyCorrect) {
                indexes.createIndex(new Index().on(EMAIL, Sort.Direction.ASC));
            }

            log.info("Customer indexes ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure customer indexes on {} shard — lookups by email will "
                    + "scan the collection until this succeeds", region, ex);
        }
    }

    /** Whether an index covers exactly these fields, in this order. */
    private boolean coversExactly(IndexInfo index, List<String> fields) {
        List<IndexField> actual = index.getIndexFields();
        if (actual.size() != fields.size()) {
            return false;
        }
        for (int i = 0; i < fields.size(); i++) {
            if (!fields.get(i).equals(actual.get(i).getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * How long a handled inbound event is remembered (Milestone 1: "a short retention
     * window"). Long enough that any retry SMP is going to make has been made -- their retry
     * is on 5xx and timeout, on the order of minutes -- and short enough that the collection
     * does not grow with every booking ever changed.
     *
     * <p>Changing this value is not a redeploy. Mongo refuses to create a TTL index whose
     * expiry differs from the one already there ({@code IndexOptionsConflict}, the same error
     * the administrator index logs), so the existing index has to be dropped, or altered with
     * {@code collMod}, before the new value takes.
     */
    private static final java.time.Duration INBOUND_EVENT_RETENTION = java.time.Duration.ofDays(7);

    /**
     * Inbound integration events (Req 4.M3, §11.7): the replay guard, and its own expiry.
     */
    private void ensureIntegrationEventIndexes(MongoTemplate template, Region region) {
        try {
            // Expires a handled event a week after it was handled. Only documents carrying a
            // date in handledAt are eligible, which is what makes an unfinished event -- one
            // whose listeners never returned -- immune: it stays until a replay completes it.
            // The _id is the eventId, so replay lookup needs no index of its own.
            template.indexOps(IntegrationEvent.class)
                    .createIndex(new Index().on("handledAt", Sort.Direction.ASC)
                            .expire(INBOUND_EVENT_RETENTION));

            log.info("Integration event indexes ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure integration event indexes on {} shard - replays are "
                    + "still recognised, but handled events will not expire", region, ex);
        }
    }

    /**
     * Notification indexes (Req 3.1.S5, §9.1, §9.2).
     */
    private void ensureNotificationIndexes(MongoTemplate template, Region region) {
        try {
            IndexOperations indexes = template.indexOps(Notification.class);
            dropSupersededNotificationIndexes(indexes, region);

            // §9.1, "newest first" for one recipient -- the bell menu's main query.
            //
            // _id closes the sort. BSON stores an Instant to the millisecond, so two
            // notifications raised from the same SMP event batch can share a createdAt; with
            // offset paging, tied documents are free to move between pages and the reader
            // sees one twice or not at all. _id is unique, so the order becomes total.
            // Keeping it in the index means the sort is still served by a walk of the index
            // rather than a blocking sort in memory.
            indexes.createIndex(new Index()
                    .on("recipientIdentifier", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC));

            // §9.1 with unreadOnly=true, and the badge count. Separate rather than folded
            // into the one above: Mongo cannot skip a middle key to reach the sort field,
            // so a single compound index would leave one of the two queries unserved.
            indexes.createIndex(new Index()
                    .on("recipientIdentifier", Sort.Direction.ASC)
                    .on("readStatus", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC));

            // The retry sweeper's query: what is still outstanding, oldest attempt first.
            // Matches few documents in practice, which is the point -- without it the
            // sweeper would scan every notification ever sent, on a schedule.
            indexes.createIndex(new Index()
                    .on("deliveryStatus", Sort.Direction.ASC)
                    .on("lastAttemptAt", Sort.Direction.ASC));

            // One notification per SMP event (§11.7, Req 3.3.U2). The service looks the event
            // up before it writes; this is what holds when two replays of the same event
            // arrive together and both find nothing. Sparse, because everything not raised
            // from an event carries no id here, and a unique index that counted nulls would
            // permit exactly one such notification in each store.
            indexes.createIndex(new Index()
                    .on("sourceEventId", Sort.Direction.ASC)
                    .unique()
                    .sparse());

            log.info("Notification indexes ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure notification indexes on {} shard - reads and the "
                    + "retry sweep will work but scan collections", region, ex);
        }
    }

    /**
     * Removes the two-key and three-key list indexes that the {@code _id} tie-breaker
     * supersedes.
     *
     * <p>Each is a prefix of its replacement, so it can serve nothing the longer one cannot,
     * and leaving it would cost a write on every notification for no read it makes faster.
     * A no-op after the first run, and on a fresh database.
     */
    private void dropSupersededNotificationIndexes(IndexOperations indexes, Region region) {
        List<List<String>> superseded = List.of(
                List.of("recipientIdentifier", "createdAt"),
                List.of("recipientIdentifier", "readStatus", "createdAt"));

        for (IndexInfo existing : indexes.getIndexInfo()) {
            for (List<String> keys : superseded) {
                if (coversExactly(existing, keys)) {
                    indexes.dropIndex(existing.getName());
                    log.info("Dropped notification index {} on {} shard: superseded by the "
                            + "same keys plus _id", existing.getName(), region);
                }
            }
        }
    }
}