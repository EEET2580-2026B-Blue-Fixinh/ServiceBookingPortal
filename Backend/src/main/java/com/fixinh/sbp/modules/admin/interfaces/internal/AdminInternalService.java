package com.fixinh.sbp.modules.admin.interfaces.internal;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.modules.admin.dto.external.StaffDto;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;
import com.fixinh.sbp.modules.admin.dto.internal.StaffUpdateCommand;
import com.fixinh.sbp.modules.admin.model.Administrator;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;

/** What other modules — and the admin controllers — may ask of the admin module. */
public interface AdminInternalService {

	/** The profile behind a token's {@code sub}, or empty if this id is not an administrator's. */
	Optional<Administrator> findById(String id);

	List<CustomerDto> listCustomers();

	CustomerDto setCustomerStatus(String customerId, AccountStatus status);

	List<StaffDto> listStaff(String query);

	/** @throws com.fixinh.sbp.common.exception.ApiException STAFF_NOT_FOUND if no such staff member */
	StaffDto getStaff(String staffId);

	/** @throws com.fixinh.sbp.common.exception.ApiException EMAIL_ALREADY_REGISTERED on a duplicate */
	StaffDto createStaff(StaffCreateCommand command);

	/** @throws com.fixinh.sbp.common.exception.ApiException STAFF_NOT_FOUND if no such staff member */
	StaffDto updateStaff(String staffId, StaffUpdateCommand command);
}
