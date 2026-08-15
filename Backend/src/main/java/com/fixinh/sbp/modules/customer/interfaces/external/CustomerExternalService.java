package com.fixinh.sbp.modules.customer.interfaces.external;

import java.util.List;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.customer.model.AccountStatus;

/** What other modules may ask of the customer module (Req A.2.4). */
public interface CustomerExternalService {

	List<CustomerDto> listAll();

	/** @throws ApiException CUSTOMER_NOT_FOUND if no customer has this id */
	CustomerDto setAccountStatus(String customerId, AccountStatus status);
}
