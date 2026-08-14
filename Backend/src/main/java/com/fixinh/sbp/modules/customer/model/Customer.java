package com.fixinh.sbp.modules.customer.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fixinh.sbp.common.sharding.Region;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer account: credentials and profile in one document.
 *
 * <p>The task board lists "UserCredential" and the customer profile separately, but the API
 * returns them as one resource (register's 201 body, GET /customers/me), so they are one
 * document here rather than two collections joined on every sign-in.
 *
 * <p><b>This document exists in both shards.</b> The shard named by {@link #homeRegion} holds the
 * authoritative copy; the other holds a replica carrying only the fields sign-in needs, so login
 * — which has an identifier but no province — never has to guess a region. See
 * {@code ShardedCustomerRepository}.
 *
 * <p><b>Consequence for whoever builds PUT /customers/me:</b> a change to any replicated field
 * ({@code fullName}, {@code phoneNumber}, {@code accountStatus}, ...) must be written to both
 * shards, or the two copies drift. Only {@link #address} is safe to write to the home shard
 * alone, because the replica deliberately does not carry it.
 */
@Document(collection = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

	/** Mongo's _id, and the customerId the API exposes. Identical across both shards. */
	@Id
	private String id;

	/**
	 * Unique system-wide (Req 3.1.S2). Stored lower-cased so lookups are case-insensitive
	 * without needing a collation.
	 */
	@Indexed(unique = true)
	private String email;

	/** Unique system-wide. */
	@Indexed(unique = true)
	private String phoneNumber;

	/** BCrypt hash. No endpoint ever returns this, and it is never logged. */
	private String passwordHash;

	private String fullName;

	private Role role;

	private AccountStatus accountStatus;

	/**
	 * Present only in the {@link #homeRegion} shard; deliberately null on the replica, because
	 * the address is the one genuinely region-owned part of the record.
	 */
	private Address address;

	/** How this account can sign in — PASSWORD, GOOGLE, or both (Appendix A). */
	@Builder.Default
	private List<String> authProviders = new ArrayList<>();

	private Instant registeredAt;

	/** Which shard holds the authoritative copy. Set from the address province on creation. */
	private Region homeRegion;
}
