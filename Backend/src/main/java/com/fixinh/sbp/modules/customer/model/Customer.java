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
 * <p><b>This document exists in exactly one shard</b> — the one named by {@link #homeRegion},
 * resolved from the address province (Req 3.1.U1). Neither store holds customers belonging to the
 * other region, so there is no copy to keep in step. See {@code ShardedCustomerRepository}.
 *
 * <p><b>Consequence for whoever builds PUT /customers/me:</b> every field can be written to the
 * home shard alone. But changing the address province changes the owning region, which is a move
 * between stores — an insert into the new shard and a delete from the old — not an update in place.
 */
@Document(collection = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

	/** Mongo's _id, and the customerId the API exposes. */
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

	/** The province here is what decides {@link #homeRegion}, and so which store owns the record. */
	private Address address;

	/** How this account can sign in — PASSWORD, GOOGLE, or both (Appendix A). */
	@Builder.Default
	private List<String> authProviders = new ArrayList<>();

	private Instant registeredAt;

	/** Which shard holds the authoritative copy. Set from the address province on creation. */
	private Region homeRegion;
}
