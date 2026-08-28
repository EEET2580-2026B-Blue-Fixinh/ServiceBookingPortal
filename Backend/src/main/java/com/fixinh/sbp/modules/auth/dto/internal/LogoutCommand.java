package com.fixinh.sbp.modules.auth.dto.internal;

import java.time.Instant;

/** Built by the controller from the authenticated {@code UserPrincipal} — logout revokes
 * exactly the token that authenticated the request, never one named in the request body. */
public record LogoutCommand(String credentialId, String tokenId, Instant tokenExpiresAt) {
}
