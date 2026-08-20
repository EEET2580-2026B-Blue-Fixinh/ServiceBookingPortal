package com.fixinh.sbp.common.smp.dto;

/**
 * Fields sent to SMP on update (API Documentation Section 10.7). Email and role are not
 * editable in this release — both are identity-bearing and changing them needs an SMT-side flow
 * that does not yet exist.
 */
public record SmpStaffUpdateRequest(
		String fullName,
		String phoneNumber,
		SmpAddress address,
		/** Optional — null leaves employment status unchanged. */
		EmploymentStatus employmentStatus) {
}
