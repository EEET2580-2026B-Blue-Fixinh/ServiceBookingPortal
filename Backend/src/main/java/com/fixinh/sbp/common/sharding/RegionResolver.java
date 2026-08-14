package com.fixinh.sbp.common.sharding;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.reference.model.Province;

/**
 * Maps a province code onto the regional store that owns the customer (Req 3.1.U1).
 *
 * <p>The client never sees or supplies a region — it sends a province code and the repository
 * resolves the rest (Req 3.1.U2).
 *
 * <p>The province catalogue itself lives in {@link Province}, which is also what
 * {@code GET /reference/provinces} publishes. Keeping one table means a province cannot be
 * offered in the dropdown but be unroutable here, which is the failure a second copy invites.
 */
@Component
public class RegionResolver {

	/**
	 * @return the owning region, or empty when the code is not a province we publish. Callers
	 *         reject unknown codes rather than defaulting to a region — guessing would file a
	 *         customer in the wrong store.
	 */
	public Optional<Region> lookup(String provinceCode) {
		return Province.byCode(provinceCode).map(Province::getRegion);
	}

	public boolean isKnown(String provinceCode) {
		return lookup(provinceCode).isPresent();
	}

	/**
	 * @throws IllegalArgumentException when the code is unknown. Request-facing callers should
	 *         validate with {@link #isKnown(String)} first so the client gets a
	 *         400 PROVINCE_CODE_INVALID instead of a 500.
	 */
	public Region resolve(String provinceCode) {
		return lookup(provinceCode).orElseThrow(() -> new IllegalArgumentException(
				"Unknown province code: " + provinceCode));
	}
}
