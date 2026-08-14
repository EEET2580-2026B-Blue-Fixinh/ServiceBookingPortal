package com.fixinh.sbp.modules.customer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Vietnamese address (Req 3.1.S3). Embedded in its owning document, never its own collection.
 *
 * <p>Only {@code provinceCode} is stored — the display name is resolved from reference data, so
 * a renamed province does not leave stale copies scattered across customer records.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

	private String streetNumber;
	private String streetName;
	private String ward;

	/** A code from GET /reference/provinces; also decides the owning shard (Req 3.1.U1). */
	private String provinceCode;
}
