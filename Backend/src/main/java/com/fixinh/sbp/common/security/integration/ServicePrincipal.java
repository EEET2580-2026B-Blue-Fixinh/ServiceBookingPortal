package com.fixinh.sbp.common.security.integration;

import java.security.Principal;

/**
 * The caller on an {@code /integration} request once the shared secret has matched: the
 * Service Management Portal, and nothing more specific than that (§11.1, Req 4.M2).
 *
 * <p>Deliberately not a {@code UserPrincipal}. That record is a person rebuilt from a JWT —
 * an id that owns bookings, an email, a token to blacklist on sign-out. None of it applies to
 * a service, and a null in each of those fields would be a lie that every ownership check
 * downstream would have to remember to ask about. The name is §11.1's {@code clientId}, so a
 * log line reads the same whichever authentication scheme is behind it.
 *
 * @param clientId who this is, in §11.1's vocabulary
 */
public record ServicePrincipal(String clientId) implements Principal {

	/** The one service identity SBP knows. */
	public static final ServicePrincipal SMP = new ServicePrincipal("smp-service");

	@Override
	public String getName() {
		return clientId;
	}
}
