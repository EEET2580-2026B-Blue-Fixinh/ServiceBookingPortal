package com.fixinh.sbp.modules.integration.model;

/**
 * Which way an integration event travelled (Milestone 1's {@code integrationEvents}
 * discriminator). One collection serves as both inbox and outbox; this is what keeps the two
 * apart.
 */
public enum EventDirection {

	/** Received from SMP over §11.7. Kept so a replayed {@code eventId} can be recognised. */
	IN,

	/**
	 * Owed to SMP. Nothing writes one yet; the value exists so the collection's shape matches
	 * the design and an outbox can land beside the inbox without a migration.
	 */
	OUT
}
