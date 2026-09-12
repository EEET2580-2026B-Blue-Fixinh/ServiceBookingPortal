package com.fixinh.sbp.common.sharding;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

/**
 * The Shard Directory kept as two copies, one in each store, and read from whichever answers
 * first — so resolving a region never needs a cross-shard hop.
 *
 * <p>A write counts once either copy accepts it, because a lookup asks both. Only when
 * neither does is the subject unroutable, and that is the one case that throws.
 */
@Repository
@Slf4j
public class ReplicatedShardDirectoryRepository implements ShardDirectoryRepository {

	private final MongoTemplate south;
	private final MongoTemplate north;

	public ReplicatedShardDirectoryRepository(
			@Qualifier(ShardMongoConfig.SOUTH_TEMPLATE) MongoTemplate south,
			@Qualifier(ShardMongoConfig.NORTH_TEMPLATE) MongoTemplate north) {
		this.south = south;
		this.north = north;
	}

	@Override
	public Optional<Region> findRegion(SubjectType subjectType, String subjectIdentifier) {
		if (subjectType == null || subjectIdentifier == null || subjectIdentifier.isBlank()) {
			return Optional.empty();
		}

		String key = ShardDirectoryEntry.keyFor(subjectType, subjectIdentifier);

		// Either copy is authoritative, so the first to answer wins. South is asked first only
		// to match ShardedCustomerRepository; nothing else rides on the order.
		ShardDirectoryEntry entry = south.findById(key, ShardDirectoryEntry.class);
		if (entry == null) {
			entry = north.findById(key, ShardDirectoryEntry.class);
		}

		return Optional.ofNullable(entry).map(ShardDirectoryEntry::getShardRegion);
	}

	@Override
	public Region register(SubjectType subjectType, String subjectIdentifier, Region region) {
		if (subjectType == null || subjectIdentifier == null || subjectIdentifier.isBlank()) {
			throw new IllegalArgumentException(
					"A shard directory entry needs both a subject type and an identifier.");
		}
		if (region == null) {
			throw new IllegalArgumentException(
					"Refusing to register " + subjectIdentifier + " with no region. A guess here "
							+ "files the subject in the wrong store, silently and permanently.");
		}

		ShardDirectoryEntry entry =
				ShardDirectoryEntry.of(subjectType, subjectIdentifier, region, Instant.now());

		boolean inSouth = replicateTo(south, Region.SOUTH, entry);
		boolean inNorth = replicateTo(north, Region.NORTH, entry);

		if (!inSouth && !inNorth) {
			throw new IllegalStateException(
					"Neither store accepted shard directory entry " + entry.getId()
							+ ", so nothing can be routed to this subject.");
		}

		return region;
	}

	private boolean replicateTo(MongoTemplate template, Region store, ShardDirectoryEntry entry) {
		try {
			template.save(entry);
			return true;
		}
		catch (RuntimeException ex) {
			log.warn("Could not replicate shard directory entry {} to the {} store - the other "
					+ "copy still answers lookups", entry.getId(), store, ex);
			return false;
		}
	}
}