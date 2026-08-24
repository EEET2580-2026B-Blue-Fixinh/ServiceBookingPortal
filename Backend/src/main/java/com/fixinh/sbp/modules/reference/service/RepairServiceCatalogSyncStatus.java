package com.fixinh.sbp.modules.reference.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * When the repair-service catalogue was last pulled from SMP (API Documentation §5.2's
 * {@code lastSyncedAt}).
 *
 * <p>Written once by {@link RepairServiceCatalogSeeder} after each boot-time sync, read by
 * {@link ReferenceServiceImpl} on every {@code GET /reference/repair-services}. {@code null}
 * until the first sync completes — there is a narrow window after the embedded server starts but
 * before the seeder's {@code ApplicationRunner} has run; accepted rather than gated, since
 * nothing else in this codebase guards that boot-time race either.
 */
@Component
public class RepairServiceCatalogSyncStatus {

	private volatile Instant lastSyncedAt;

	/** Package-private: only the seeder that owns this timestamp may set it. */
	void recordSync(Instant when) {
		this.lastSyncedAt = when;
	}

	public Instant lastSyncedAt() {
		return lastSyncedAt;
	}
}
