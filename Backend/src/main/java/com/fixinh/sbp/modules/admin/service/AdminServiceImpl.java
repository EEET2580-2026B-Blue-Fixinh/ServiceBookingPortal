package com.fixinh.sbp.modules.admin.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;
import com.fixinh.sbp.modules.admin.dto.external.StaffDto;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;
import com.fixinh.sbp.modules.admin.dto.internal.StaffUpdateCommand;
import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;
import com.fixinh.sbp.modules.admin.model.Administrator;
import com.fixinh.sbp.modules.admin.repository.AdministratorRepository;
import com.fixinh.sbp.modules.auth.interfaces.internal.AuthInternalService;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

import lombok.extern.slf4j.Slf4j;

/**
 * The admin module's own logic: the profile lookup sign-in needs, plus customer and staff
 * account management (Req 3.5.S1, 3.5.S2).
 */
@Service
@Slf4j
public class AdminServiceImpl implements AdminInternalService {

	private final AdministratorRepository administratorRepository;
	private final CustomerExternalService customerService;
	private final SmpStaffClient smpStaffClient;
	private final ReferenceExternalService referenceService;

	/**
	 * Lazy because the dependency runs the other way at sign-in: auth resolves an
	 * administrator's display name through this module (see {@code AccountProfileReader}), so an
	 * eager reference here would be circular. Only {@link #listCustomers} and
	 * {@link #setCustomerStatus} use it, both well after sign-in, so paying for the proxy once is
	 * cheap.
	 */
	private final AuthInternalService authService;

	/**
	 * Written out by hand rather than via {@code @RequiredArgsConstructor}: Lombok does not copy
	 * a field's {@code @Lazy} onto the generated constructor parameter, so the annotation would
	 * silently do nothing and the circular-bean error would come back.
	 */
	public AdminServiceImpl(
			AdministratorRepository administratorRepository,
			CustomerExternalService customerService,
			SmpStaffClient smpStaffClient,
			ReferenceExternalService referenceService,
			@Lazy AuthInternalService authService) {
		this.administratorRepository = administratorRepository;
		this.customerService = customerService;
		this.smpStaffClient = smpStaffClient;
		this.referenceService = referenceService;
		this.authService = authService;
	}

	@Override
	public Optional<Administrator> findById(String id) {
		return administratorRepository.findById(id);
	}

	@Override
	public List<CustomerDto> listCustomers() {
		var credentialsById = authService.listByRole(Role.CUSTOMER).stream()
				.collect(Collectors.toMap(UserCredential::getId, credential -> credential));

		return customerService.listAll().stream()
				.map(profile -> toDto(profile, credentialsById.get(profile.getId())))
				.toList();
	}

	@Override
	public CustomerDto setCustomerStatus(String customerId, AccountStatus status) {
		UserCredential credential = authService.setAccountStatus(customerId, status);
		Customer profile = customerService.findById(customerId)
				.orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));

		return toDto(profile, credential);
	}

	private CustomerDto toDto(Customer profile, UserCredential credential) {
		String phoneNumber = credential != null ? credential.getPhoneNumber() : null;
		AccountStatus status = credential != null ? credential.getAccountStatus() : null;
		return CustomerDto.from(profile, phoneNumber, status);
	}

	@Override
	public List<StaffDto> listStaff() {
		return smpStaffClient.listStaff().stream()
				.map(staff -> StaffDto.listItem(staff, branchProvinceName(staff.branchProvinceCode())))
				.toList();
	}

	@Override
	public StaffDto getStaff(String staffId) {
		SmpStaffDto staff = smpStaffClient.findStaff(staffId)
				.orElseThrow(() -> new ApiException(ErrorCode.STAFF_NOT_FOUND));
		return StaffDto.detail(staff, branchProvinceName(staff.branchProvinceCode()));
	}

	@Override
	public StaffDto createStaff(StaffCreateCommand command) {
		SmpStaffDto created;
		try {
			created = smpStaffClient.createStaff(new SmpStaffCreateRequest(
					command.fullName(),
					command.email(),
					command.phoneNumber(),
					command.staffRole(),
					command.address(),
					command.branchProvinceCode()));
		} catch (SmpDuplicateEmailException ex) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}

		return StaffDto.created(created, sendWelcomeEmail(created));
	}

	@Override
	public StaffDto updateStaff(String staffId, StaffUpdateCommand command) {
		SmpStaffDto updated;
		try {
			updated = smpStaffClient.updateStaff(staffId, new SmpStaffUpdateRequest(
					command.fullName(),
					command.phoneNumber(),
					command.address(),
					command.employmentStatus()));
		} catch (NoSuchElementException ex) {
			throw new ApiException(ErrorCode.STAFF_NOT_FOUND);
		}

		return StaffDto.detail(updated, branchProvinceName(updated.branchProvinceCode()));
	}

	private String branchProvinceName(String branchProvinceCode) {
		return referenceService.listProvinces().stream()
				.filter(province -> province.provinceCode().equals(branchProvinceCode))
				.findFirst()
				.map(ProvinceDto::provinceName)
				.orElse(branchProvinceCode);
	}

	/**
	 * Req 3.1.S5: SBP sends the welcome email carrying {@link SmpStaffDto#initialPassword()}.
	 * The notification module is not built yet (still an empty scaffold), so this cannot send
	 * anything today — {@code notificationSent} honestly reports that, rather than lying to the
	 * administrator. Replace with a real call once that module lands.
	 */
	private boolean sendWelcomeEmail(SmpStaffDto staff) {
		log.warn("Welcome email for new staff member {} was not sent: the notification module "
				+ "is not implemented yet.", staff.staffId());
		return false;
	}
}
