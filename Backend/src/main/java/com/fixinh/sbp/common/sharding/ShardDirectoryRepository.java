package com.fixinh.sbp.common.sharding;

import java.util.Optional;

/**
 * Where each subject's data lives — the CDM's Shard Directory, behind an interface.
 *
 * <p>What lets the notification module route a staff member it holds no record of: SMP owns
 * staff, so SBP keeps only an identifier and the store it belongs to (see
 * {@link ShardDirectoryEntry}).
 */
public interface ShardDirectoryRepository {

	/**
	 * Where this subject lives, if the directory has been told.
	 *
	 * @return empty when the subject is unknown. That is an ordinary outcome — every subject is
	 *         unknown until something registers it — not a fault.
	 */
	Optional<Region> findRegion(SubjectType subjectType, String subjectIdentifier);

	/**
	 * Records where a subject lives, replicating to both stores.
	 *
	 * <p>Idempotent: registering the same subject again overwrites the entry rather than adding
	 * a second one, because the {@code _id} is derived from the subject.
	 *
	 * @return the region that was registered, so a call site can chain it onto a lookup miss
	 * @throws IllegalStateException if neither store accepted the write. One is enough —
	 *         {@link #findRegion} asks both — so this means the directory is entirely
	 *         unreachable and nothing can be routed.
	 */
	Region register(SubjectType subjectType, String subjectIdentifier, Region region);
}