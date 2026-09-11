package com.fixinh.sbp.modules.admin.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.interfaces.internal.NotificationInternalService;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

/**
 * The admin module's own logic: the profile lookup sign-in needs, plus customer and staff
 * account management (Req 3.5.S1, 3.5.S2).
 */
@Service
public class AdminServiceImpl implements AdminInternalService {

	private final AdministratorRepository administratorRepository;
	private final CustomerExternalService customerService;
	private final SmpStaffClient smpStaffClient;
	private final ReferenceExternalService referenceService;
	private final NotificationInternalService notificationService;
	private final StaffCreateValidator staffCreateValidator;

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
			NotificationInternalService notificationService,
			StaffCreateValidator staffCreateValidator,
			@Lazy AuthInternalService authService) {
		this.administratorRepository = administratorRepository;
		this.customerService = customerService;
		this.smpStaffClient = smpStaffClient;
		this.referenceService = referenceService;
		this.notificationService = notificationService;
		this.staffCreateValidator = staffCreateValidator;
		this.authService = authService;
	}

	@Override
	public Optional<Administrator> findById(String id) {
		return administratorRepository.findById(id);
	}

	@Override
	public List<CustomerDto> listCustomers() {
		// Both listByRole and listAll concatenate the north and south shards without
		// deduplicating. Each customer is supposed to live in exactly one shard, but a
		// stray record left in both (e.g. an incomplete region relocation) must not take
		// down the whole listing - keep the first copy seen rather than throwing.
		var credentialsById = authService.listByRole(Role.CUSTOMER).stream()
				.collect(Collectors.toMap(UserCredential::getId, credential -> credential,
						(first, duplicate) -> first));

		var profilesById = customerService.listAll().stream()
				.collect(Collectors.toMap(Customer::getId, profile -> profile,
						(first, duplicate) -> first, LinkedHashMap::new));

		return profilesById.values().stream()
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
	public List<StaffDto> listStaff(String query) {
		// Built once per call rather than re-scanning the province list per row.
		Map<String, String> provinceNames = provinceNamesByCode();
		return smpStaffClient.listStaff(query).stream()
				.map(staff -> StaffDto.listItem(staff,
						provinceNames.getOrDefault(staff.branchProvinceCode(), staff.branchProvinceCode())))
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
		staffCreateValidator.validate(command);

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

		StaffWelcomeEmailResult emailResult = notificationService.sendStaffWelcomeEmail(
				new StaffWelcomeEmailCommand(
						created.staffId(),
						created.fullName(),
						created.email(),
						created.staffRole().name(),
						created.initialPassword(),
						branchProvinceFor(created, command)));

		if (!emailResult.sent()) {
			throw new ApiException(ErrorCode.EMAIL_DELIVERY_FAILED);
		}

		return StaffDto.created(created, true);
	}

	/**
	 * The branch province the welcome email's notification is filed under: SMP's, when its
	 * response carries one, otherwise the one the administrator sent.
	 *
	 * <p>SMP's answer wins because the account is SMP's record. The fallback exists because
	 * §12.8 is provisional and does not promise SMP echoes the province back. Without it, a
	 * response that omits the field would turn an account SMP has <i>already created</i> into
	 * a 400 {@code PROVINCE_CODE_INVALID} — no email sent, and no notification for the retry
	 * sweep to find. The administrator's code is a safe stand-in: {@link StaffCreateValidator}
	 * checked it against the province list before SMP was ever called.
	 */
	private static String branchProvinceFor(SmpStaffDto created, StaffCreateCommand command) {
		String fromSmp = created.branchProvinceCode();
		return fromSmp == null || fromSmp.isBlank() ? command.branchProvinceCode() : fromSmp;
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
		return provinceNamesByCode().getOrDefault(branchProvinceCode, branchProvinceCode);
	}

	private Map<String, String> provinceNamesByCode() {
		return referenceService.listProvinces().stream()
				.collect(Collectors.toMap(ProvinceDto::provinceCode, ProvinceDto::provinceName, (first, duplicate) -> first));
	}
}
