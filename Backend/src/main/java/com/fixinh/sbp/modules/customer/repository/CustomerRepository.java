package com.fixinh.sbp.modules.customer.repository;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.customer.model.Customer;

/**
 * Customer profile access, spanning both regional stores (Req 3.1.U2).
 *
 * <p>Deliberately a plain interface rather than a Spring Data {@code MongoRepository}: derived
 * queries bind to a single {@code MongoTemplate}, and these methods have to reach two.
 *
 * <p>No lookup by email or phone here any more — sign-in resolves those against the credential
 * store, which is in both databases and so answers in one read. Callers never name a shard.
 */
public interface CustomerRepository {

	/** Persists to the shard the address province resolves to, and only that shard. */
	Customer save(Customer customer);

	/** Writes the profile to its new shard, then removes the copy from the previous shard. */
	Customer relocate(Customer customer, Region previousRegion);

	/**
	 * The profile for a signed-in caller, whose id came from their token. Also what the booking
	 * module resolves a customer's home region through (see
	 * {@code CustomerServiceImpl.findHomeRegion}), since a Mongo id carries no region either.
	 */
	Optional<Customer> findById(String id);

	/** Every customer profile system-wide, for the admin customer list (Req 3.5.S2). */
	List<Customer> findAll();
}
