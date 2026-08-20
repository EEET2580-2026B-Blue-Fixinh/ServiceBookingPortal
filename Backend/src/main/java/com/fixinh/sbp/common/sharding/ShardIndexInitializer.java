package com.fixinh.sbp.common.sharding;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
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
     * Failures are logged, not thrown: an unreachable shard should degrade that
     * region, not stop the application serving the other one.
     */
    private void ensureCustomerIndexes(MongoTemplate template, Region region) {
        try {
            template.indexOps(Customer.class)
                    .createIndex(new Index().on("email", Sort.Direction.ASC).unique());
            template.indexOps(Customer.class)
                    .createIndex(new Index().on("phoneNumber", Sort.Direction.ASC).unique());

            log.info("Customer indexes ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure customer indexes on {} shard — uniqueness is "
                    + "NOT enforced there until this succeeds", region, ex);
        }
    }

    /**
     * Notification indexes (Req 3.1.S5, and §9.1/§9.2 ahead of issue 5.0).
     */
    private void ensureNotificationIndexes(MongoTemplate template, Region region) {
        try {
            // §9.1, "newest first" for one recipient -- the bell menu's main query.
            template.indexOps(Notification.class).createIndex(
                    new Index().on("recipientIdentifier", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC));

            // §9.1 with unreadOnly=true, and the badge count. Separate rather than folded
            // into the one above: Mongo cannot skip a middle key to reach the sort field,
            // so a single compound index would leave one of the two queries unserved.
            template.indexOps(Notification.class).createIndex(
                    new Index().on("recipientIdentifier", Sort.Direction.ASC)
                            .on("readStatus", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC));

            // The retry sweeper's query: what is still outstanding, oldest attempt first.
            // Matches few documents in practice, which is the point -- without it the
            // sweeper would scan every notification ever sent, on a schedule.
            template.indexOps(Notification.class).createIndex(
                    new Index().on("deliveryStatus", Sort.Direction.ASC)
                            .on("lastAttemptAt", Sort.Direction.ASC));

            log.info("Notification indexes ensured on {} shard ({})", region,
                    template.getDb().getName());
        }
        catch (RuntimeException ex) {
            log.warn("Could not ensure notification indexes on {} shard - reads and the "
                    + "retry sweep will work but scan collections", region, ex);
        }
    }
}