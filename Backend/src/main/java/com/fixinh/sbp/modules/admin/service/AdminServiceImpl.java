package com.fixinh.sbp.modules.admin.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;
import com.fixinh.sbp.modules.admin.model.Administrator;
import com.fixinh.sbp.modules.admin.repository.AdministratorRepository;

import lombok.RequiredArgsConstructor;

/**
 * The admin module's own logic. Only the profile lookup sign-in needs is implemented; the staff
 * and customer-administration endpoints belong to a separate task.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminInternalService {

	private final AdministratorRepository administratorRepository;

	@Override
	public Optional<Administrator> findById(String id) {
		return administratorRepository.findById(id);
	}
}
