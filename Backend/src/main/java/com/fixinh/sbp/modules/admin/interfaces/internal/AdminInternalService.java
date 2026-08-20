package com.fixinh.sbp.modules.admin.interfaces.internal;

import java.util.List;

import com.fixinh.sbp.modules.admin.dto.external.StaffDto;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;
import com.fixinh.sbp.modules.admin.dto.internal.StaffUpdateCommand;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.customer.model.AccountStatus;

/** Business logic behind the admin controllers (Req 3.5.S1, 3.5.S2). */
public interface AdminInternalService {

	List<CustomerDto> listCustomers();

	CustomerDto setCustomerStatus(String customerId, AccountStatus status);

	List<StaffDto> listStaff();

	/** @throws com.fixinh.sbp.common.exception.ApiException STAFF_NOT_FOUND if no such staff member */
	StaffDto getStaff(String staffId);

	/** @throws com.fixinh.sbp.common.exception.ApiException EMAIL_ALREADY_REGISTERED on a duplicate */
	StaffDto createStaff(StaffCreateCommand command);

	/** @throws com.fixinh.sbp.common.exception.ApiException STAFF_NOT_FOUND if no such staff member */
	StaffDto updateStaff(String staffId, StaffUpdateCommand command);
}
