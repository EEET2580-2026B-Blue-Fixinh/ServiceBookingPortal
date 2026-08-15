package com.fixinh.sbp.modules.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.model.AccountStatus;

import lombok.RequiredArgsConstructor;

/** Delegates customer account management to the customer module (Req A.2.4). */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminInternalService {

	private final CustomerExternalService customerService;

	@Override
	public List<CustomerDto> listCustomers() {
		return customerService.listAll();
	}

	@Override
	public CustomerDto setCustomerStatus(String customerId, AccountStatus status) {
		return customerService.setAccountStatus(customerId, status);
	}
}
