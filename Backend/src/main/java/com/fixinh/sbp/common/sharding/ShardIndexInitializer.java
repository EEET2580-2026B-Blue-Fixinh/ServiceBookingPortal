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
import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.notification.model.Notification;

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
        ensureNotificationIndexes(south, Region.SOUTH);
        ensureNotificationIndexes(north, Region.NORTH);
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