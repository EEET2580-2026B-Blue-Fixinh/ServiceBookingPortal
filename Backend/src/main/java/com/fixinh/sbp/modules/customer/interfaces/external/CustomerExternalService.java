package com.fixinh.sbp.modules.customer.interfaces.external;

import java.util.Optional;

import com.fixinh.sbp.common.sharding.Region;

/**
 * What other modules may ask of the customer module (Req A.2.4, A.3.1) — as opposed to
 * {@code CustomerInternalService}, which is reserved for the customer module's own controller.
 */
public interface CustomerExternalService {

	/**
	 * The regional store holding this customer's data (Req 3.1.U1), so another module can file a
	 * record it owns — a booking, for instance — into the same store the customer lives in.
	 *
	 * @return empty when no such customer exists, so the caller can turn that into its own
	 *         not-found error rather than a shard-resolution failure.
	 */
	Optional<Region> findHomeRegion(String customerId);
}
