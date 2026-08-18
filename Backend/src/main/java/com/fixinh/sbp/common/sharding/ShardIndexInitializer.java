package com.fixinh.sbp.common.sharding;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.customer.model.Customer;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the unique indexes both regional stores need (Req 3.1.S2), after the
 * application is up rather than while its beans are being built.
 *
 * <p>Spring Data's {@code auto-index-creation} does this inside the
 * {@code MongoTemplate} constructor, which makes a writable cluster a condition
 * of the bean existing at all — one cold or paused shard then takes the whole
 * application down, and {@code ShardHealthConfig} never gets to report which.
 * Running here instead means a shard that is asleep costs its own indexes and
 * nothing else.
 *
 * <p>{@code createIndex} is idempotent: an index that already matches is left
 * alone, so this is safe on every boot.
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
}