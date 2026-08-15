package com.fixinh.sbp.modules.admin.interfaces.internal;

import java.util.List;

import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.customer.model.AccountStatus;

/** Business logic behind the admin controllers (Req 3.5.S2). */
public interface AdminInternalService {

	List<CustomerDto> listCustomers();

	CustomerDto setCustomerStatus(String customerId, AccountStatus status);
}
