package com.fixinh.sbp.common.smp.dto;

/**
 * A staff member's address, in SMP's own shape (API Documentation Section 10.6). Kept separate
 * from the customer module's {@code Address} so {@code common.smp} does not depend on a
 * module's model (Req A.2.4).
 */
public record SmpAddress(
		String streetNumber,
		String streetName,
		String ward,
		String provinceCode) {
}
