package com.fixinh.sbp.modules.admin.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fixinh.sbp.common.security.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An administrator's profile. Credentials live in {@code UserCredential}, not here.
 *
 * <p>The counterpart to {@code Customer}: same profile fields, minus everything about being
 * somewhere. An administrator has no address, which is exactly why this is its own collection —
 * filing one as a customer would mean inventing a province purely to satisfy the shard router.
 *
 * <p><b>Stored in the south store</b>, beside the credentials: an administrator belongs to no
 * region, so no province decides where the record goes, and keeping the two together means
 * resolving an administrator after sign-in touches the store that already answered.
 */
@Document(collection = "administrators")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Administrator {

	/** Mongo's _id, and the id of the credential that identifies this profile. */
	@Id
	private String id;

	/** Denormalised from the credential; the credential remains authoritative. */
	@Indexed
	private String email;

	private String fullName;

	/** Denormalised from the credential's discriminator. Always ADMIN in this collection. */
	private Role role;

	/**
	 * The first administrator, who may create others. Distinguishes the seeded account from
	 * administrators it goes on to create.
	 */
	private boolean masterAdmin;

	private Instant registeredAt;
}
