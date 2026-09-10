package com.fixinh.sbp.modules.reference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.modules.reference.model.RepairServiceType;

/**
 * Data access for SBP's local repair-service catalogue (API Documentation §5.2, §12.5).
 *
 * <p>Deliberately a plain interface rather than a Spring Data {@code MongoRepository} — mirrors
 * {@code ServiceBookingRepository} and {@code CustomerRepository} so every module in this
 * codebase reaches Mongo the same way, even though this collection, unlike theirs, is not
 * sharded: it holds no customer data, so both regions read the one store.
 */
public interface RepairServiceTypeRepository {

	/**
	 * Inserts a new entry, or updates the existing one when {@code service.getId()} is already
	 * set — how {@code RepairServiceCatalogSeeder} keeps a re-run idempotent.
	 */
	RepairServiceType save(RepairServiceType service);

	/** The full catalogue, active and inactive services alike (API Doc §5.2). */
	List<RepairServiceType> findAll();

	/** Looked up by SMP's catalogue identifier, never Mongo's internal id. */
	Optional<RepairServiceType> findByServiceId(String serviceId);

	/**
	 * Removes every entry whose {@code serviceId} is not in {@code serviceIds} — how
	 * {@code RepairServiceCatalogSeeder} lets the replica shrink when SMP's catalogue does.
	 *
	 * @param serviceIds the identifiers SMP just returned; never empty, because deleting on an
	 *        empty catalogue would clear the collection
	 * @return how many entries were removed
	 */
	long deleteByServiceIdNotIn(Collection<String> serviceIds);
}
