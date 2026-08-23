package com.fixinh.sbp.modules.reference.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SBP's local replica of one entry in SMP's repair-service catalogue (API Documentation §5.2,
 * §12.5).
 *
 * <p>Not customer-owned data, so — unlike {@code ServiceBooking} or {@code Customer} — this
 * collection is not sharded by region (Req 3.1.U1 only shards Customer data). Every region reads
 * the same catalogue from the same store; see {@code RepairServiceTypeRepository}.
 *
 * <p>SMP owns the catalogue. This document only caches it, so the booking form and the service
 * filter keep working when SMP is unreachable (API Doc §2.8, "reads degrade") — populated by
 * {@code RepairServiceCatalogSeeder} from whichever {@code SmpBookingClient} is active.
 */
@Document(collection = "repair_service_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairServiceType {

	/** Mongo's _id. Internal only — never returned by an endpoint. */
	@Id
	private String id;

	/** SMP's catalogue identifier, e.g. {@code SVC-PLUMB-01} (API Doc §5.2). */
	@Indexed(unique = true)
	private String serviceId;

	private String serviceName;

	/**
	 * Short customer-facing copy for the booking form. SMP's catalogue carries no such field
	 * (see {@code SmpRepairServiceDto}), so this is SBP-local content — blank until a future
	 * feature writes it. The seeder never overwrites it once set.
	 */
	private String description;

	private String categoryName;

	/** VND, no decimals (API Doc §2.6). A snapshot of SMP's base fee as of the last sync. */
	private long baseServiceFee;

	private int standardDurationMinutes;

	/** false for a retired service — still stored, never deleted (API Doc §5.2). */
	private boolean active;
}
