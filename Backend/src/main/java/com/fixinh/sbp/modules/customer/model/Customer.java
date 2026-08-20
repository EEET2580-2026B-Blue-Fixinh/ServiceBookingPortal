package com.fixinh.sbp.modules.customer.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.sharding.Region;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer's profile. Credentials live in {@code UserCredential}, not here.
 *
 * <p>{@link #id} is the same value as the credential that identifies this profile, so the two
 * are joined without a lookup key and the token's {@code sub} names both.
 *
 * <p><b>This document exists in exactly one shard</b> — the one named by {@link #homeRegion},
 * resolved from the address province (Req 3.1.U1). Neither store holds customers belonging to the
 * other region, so there is no copy to keep in step.
 *
 * <p><b>Consequence for whoever builds PUT /customers/me:</b> most fields can be written to the
 * home shard alone. Two exceptions. Changing the address province changes the owning region — a
 * move between stores, an insert into the new shard and a delete from the old, not an update in
 * place. And {@link #email} is a denormalised copy: the credential is authoritative, so a change
 * has to be written there too or the two will disagree.
 */
@Document(collection = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

	/** Mongo's _id, the customerId the API exposes, and the credential's id. */
	@Id
	private String id;

	/**
	 * Denormalised from the credential so customer searches and listings do not have to join
	 * against another collection. Indexed for that reason, but <em>not</em> unique here —
	 * uniqueness is the credential's to enforce, and a second unique index on a copy would fail
	 * confusingly rather than usefully.
	 */
	@Indexed
	private String email;

	private String fullName;

	/**
	 * Denormalised from the credential's discriminator. Always CUSTOMER for a document in this
	 * collection; carried so a profile read knows what it is holding without a second query.
	 */
	private Role role;

	/** The province here is what decides {@link #homeRegion}, and so which store owns the record. */
	private Address address;

	private Instant registeredAt;

	/** Which shard holds the authoritative copy. Set from the address province on creation. */
	private Region homeRegion;
}
