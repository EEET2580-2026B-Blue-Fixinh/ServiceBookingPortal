package com.fixinh.sbp.modules.auth.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A verified external identity linked to an internal credential. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationIdentity {
	private AuthProvider provider;
	private String externalAccountIdentifier;
	private Instant linkedAt;
}
