package com.fixinh.sbp.modules.customer.repository;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.modules.customer.model.Customer;

/**
 * Customer data access spanning both regional stores (Req 3.1.U2).
 *
 * <p>Deliberately a plain interface rather than a Spring Data {@code MongoRepository}: derived
 * queries bind to a single {@code MongoTemplate}, and every method here has to reach two.
 *
 * <p>Uniqueness and lookups are system-wide. Callers never name a shard.
 */
public interface CustomerRepository {

	/** Persists to the shard the address province resolves to, and only that shard. */
	Customer save(Customer customer);

	Optional<Customer> findByEmail(String email);

	Optional<Customer> findByPhoneNumber(String phoneNumber);

	/** The authoritative (full-profile) copy, wherever its home shard is. */
	Optional<Customer> findById(String id);

	/** Every customer system-wide, authoritative copy only — never the sign-in replica. */
	List<Customer> findAll();

	boolean existsByEmail(String email);

	boolean existsByPhoneNumber(String phoneNumber);
}
