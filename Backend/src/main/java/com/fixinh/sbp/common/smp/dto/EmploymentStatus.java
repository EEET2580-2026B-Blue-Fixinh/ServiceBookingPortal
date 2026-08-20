package com.fixinh.sbp.common.smp.dto;

/**
 * An HR attribute SMP tracks on a staff account (API Documentation Appendix A). It does not by
 * itself deny login — {@code AccountStatus} is the switch that does that, for customers.
 */
public enum EmploymentStatus {
	ACTIVE,
	INACTIVE
}
