package com.fixinh.sbp.common.sharding;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One identifier and the regional store that owns it (CDM: Shard Directory).
 *
 * <p>Milestone 1: <i>"Maps a subject identifier to its regional shard. Present in both
 * databases and replicated to both shards inside each, so the router can resolve a region
 * without a cross-shard hop. This collection is what lets 4.U1 hold: neither portal ever
 * supplies a shard key, it passes an identifier and the directory resolves the rest."</i>
 *
 * <p><b>Why this settles where a STAFF notification goes.</b> Such a notification names a
 * recipient SBP does not hold — the {@code staff} collection is SMP's — so there is no local
 * record to read a region from. What SBP needs is two fields: an identifier and a region. That
 * is exactly what the CDM already puts here, and it is emphatically not a copy of a staff
 * member: no name, no email, no employment status, nothing to drift out of step with SMP.
 */
@Document(collection = "shardDirectory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShardDirectoryEntry {

	@Id
	private String id;

	private SubjectType subjectType;

	private String subjectIdentifier;

	/** Which of the two stores holds this subject's data. */
	private Region shardRegion;

	private Instant registeredAt;

	/** The {@code _id} for a subject, so the key is built in one place and cannot drift. */
	public static String keyFor(SubjectType subjectType, String subjectIdentifier) {
		return subjectType.name() + ":" + subjectIdentifier;
	}

	/**
	 * A factory rather than a builder: the key is derived from two of the fields, so letting a
	 * caller set them independently would let the key disagree with its own contents.
	 */
	public static ShardDirectoryEntry of(SubjectType subjectType, String subjectIdentifier,
			Region shardRegion, Instant registeredAt) {

		return new ShardDirectoryEntry(keyFor(subjectType, subjectIdentifier), subjectType,
				subjectIdentifier, shardRegion, registeredAt);
	}
}