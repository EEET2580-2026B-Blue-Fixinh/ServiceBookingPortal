package com.fixinh.sbp.modules.customer.interfaces.external;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.customer.model.Customer;

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

	/** Every customer profile system-wide, for the admin customer list (Req 3.5.S2). */
	List<Customer> listAll();

	/** A single profile, for the admin module to combine with its credential (Req 3.5.S2). */
	Optional<Customer> findById(String customerId);
}
