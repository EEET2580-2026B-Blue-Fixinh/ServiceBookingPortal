package com.fixinh.sbp.modules.admin.dto.internal;

import com.fixinh.sbp.common.security.AccountStatus;

import jakarta.validation.constraints.NotNull;

/** PATCH /admin/customers/{id}/status body (Req 3.5.S2). */
public record AdminCustomerStatusCommand(

		@NotNull(message = "Choose ACTIVE or DEACTIVATED.")
		AccountStatus status) {
}