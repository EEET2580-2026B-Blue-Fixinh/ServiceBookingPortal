package com.fixinh.sbp.common.sharding;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Liveness for each regional store, reported separately at {@code /actuator/health}.
 *
 * <p>Replaces Spring Boot's built-in Mongo indicator, which is disabled in application.yml
 * ({@code management.health.mongo.enabled: false}). That one issues {@code hello} against the
 * {@code local} database, and Atlas denies {@code local} on free and shared clusters to every
 * user — MongoDB's own {@code readWriteAnyDatabase} role excludes {@code local} and
 * {@code config}, and no Atlas role grants them on a free tier. The result was two shards
 * reporting DOWN while both were perfectly reachable.
 *
 * <p>{@code ping} against the application's own database is permitted, and is a truer check
 * anyway: it exercises the database this shard actually reads and writes, not a replica-set
 * internal we never touch.
 */
@Configuration
public class ShardHealthConfig {

	/** Bean names become the keys under {@code components} — Boot strips a HealthIndicator suffix. */
	@Bean("northShard")
	public HealthIndicator northShardHealth(
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate template) {
		return new ShardHealthIndicator(template);
	}

	@Bean("southShard")
	public HealthIndicator southShardHealth(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate template) {
		return new ShardHealthIndicator(template);
	}

	private static final class ShardHealthIndicator implements HealthIndicator {

		private final MongoTemplate template;

		private ShardHealthIndicator(MongoTemplate template) {
			this.template = template;
		}

		@Override
		public Health health() {
			String database = template.getDb().getName();
			try {
				Document result = template.getDb().runCommand(new Document("ping", 1));
				return Health.up()
						.withDetail("database", database)
						.withDetail("ok", result.get("ok"))
						.build();
			}
			catch (RuntimeException ex) {
				// Surfaces the database name alongside the cause: with two near-identical shards,
				// the exception alone rarely says which one failed.
				return Health.down(ex)
						.withDetail("database", database)
						.build();
			}
		}
	}
}
