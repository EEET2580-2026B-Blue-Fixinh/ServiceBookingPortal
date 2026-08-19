package com.fixinh.sbp.common.sharding;

/**
 * What kind of thing a {@link ShardDirectoryEntry} points at (CDM: the Shard Directory's
 * {@code subject type} discriminator, values {@code {CUSTOMER | STAFF}}).
 */
public enum SubjectType {

	/** An SBP customer. The identifier is their customer id. */
	CUSTOMER,

	/** A receptionist or technician. The identifier is SMP's staff id. */
	STAFF
}