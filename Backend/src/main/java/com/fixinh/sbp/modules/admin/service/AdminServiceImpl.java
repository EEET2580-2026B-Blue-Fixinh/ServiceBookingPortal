package com.fixinh.sbp.modules.admin.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;
import com.fixinh.sbp.modules.admin.dto.external.StaffDto;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;
import com.fixinh.sbp.modules.admin.dto.internal.StaffUpdateCommand;
import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.model.AccountStatus;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Delegates customer and staff account management to the owning modules (Req A.2.4). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminInternalService {

	private final CustomerExternalService customerService;
	private final SmpStaffClient smpStaffClient;
	private final ReferenceExternalService referenceService;

	@Override
	public List<CustomerDto> listCustomers() {
		return customerService.listAll();
	}

	@Override
	public CustomerDto setCustomerStatus(String customerId, AccountStatus status) {
		return customerService.setAccountStatus(customerId, status);
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
