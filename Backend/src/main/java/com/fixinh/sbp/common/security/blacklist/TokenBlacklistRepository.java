package com.fixinh.sbp.common.security.blacklist;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardMongoConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Revoked access tokens, replicated to both regional stores. A {@code jti} carries no region of
 * its own — unlike a credential, there is nothing to shard it by — so it is written to both
 * stores and read from whichever answers, exactly like {@code ReplicatedShardDirectoryRepository}.
 */
@Repository
@Slf4j
public class TokenBlacklistRepository {

	private final MongoTemplate south;
	private final MongoTemplate north;

	public TokenBlacklistRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south;
		this.north = north;
	}

	public void blacklist(String jti, Instant expiresAt) {
		BlacklistedToken entry = new BlacklistedToken(jti, expiresAt);
		boolean inSouth = replicateTo(south, Region.SOUTH, entry);
		boolean inNorth = replicateTo(north, Region.NORTH, entry);

		if (!inSouth && !inNorth) {
			throw new IllegalStateException(
					"Neither store accepted blacklist entry for token " + jti
							+ " — it will still work until it expires naturally.");
		}
	}

	/** Checked on every authenticated request, so south is tried first and north only on a miss. */
	public boolean isBlacklisted(String jti) {
		if (jti == null) {
			return false;
		}
		if (south.findById(jti, BlacklistedToken.class) != null) {
			return true;
		}
		return north.findById(jti, BlacklistedToken.class) != null;
	}

	private boolean replicateTo(MongoTemplate template, Region store, BlacklistedToken entry) {
		try {
			template.save(entry);
			return true;
		} catch (RuntimeException ex) {
			log.warn("Could not replicate blacklist entry {} to the {} store - the other copy "
					+ "still answers lookups", entry.getId(), store, ex);
			return false;
		}
	}
}
