package com.fixinh.sbp.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.dto.EmploymentStatus;
import com.fixinh.sbp.common.smp.dto.SmpAddress;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.StaffRole;
import com.fixinh.sbp.modules.admin.dto.external.StaffDto;
import com.fixinh.sbp.modules.customer.dto.external.CustomerDto;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;
import com.fixinh.sbp.modules.admin.dto.internal.StaffUpdateCommand;
import com.fixinh.sbp.modules.admin.repository.AdministratorRepository;
import com.fixinh.sbp.modules.auth.interfaces.internal.AuthInternalService;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailResult;
import com.fixinh.sbp.modules.notification.interfaces.internal.NotificationInternalService;
import com.fixinh.sbp.modules.reference.dto.external.ProvinceDto;
import com.fixinh.sbp.modules.reference.interfaces.external.ReferenceExternalService;

class AdminServiceImplTest {

	private AdministratorRepository administratorRepository;
	private CustomerExternalService customerService;
	private SmpStaffClient smpStaffClient;
	private ReferenceExternalService referenceService;
	private NotificationInternalService notificationService;
	private StaffCreateValidator staffCreateValidator;
	private AuthInternalService authService;
	private AdminServiceImpl service;

	@BeforeEach
	void setUp() {
		administratorRepository = mock(AdministratorRepository.class);
		customerService = mock(CustomerExternalService.class);
		smpStaffClient = mock(SmpStaffClient.class);
		referenceService = mock(ReferenceExternalService.class);
		notificationService = mock(NotificationInternalService.class);
		staffCreateValidator = mock(StaffCreateValidator.class);
		authService = mock(AuthInternalService.class);
		service = new AdminServiceImpl(administratorRepository, customerService, smpStaffClient,
				referenceService, notificationService, staffCreateValidator, authService);

		when(referenceService.listProvinces()).thenReturn(List.of(
				new ProvinceDto("HCM", "Ho Chi Minh City", true, "79"),
				new ProvinceDto("HN", "Ha Noi", true, "1")));
	}

	@Test
	void listStaffResolvesBranchProvinceNamesAndOnlyListsProvincesOnce() {
		when(smpStaffClient.listStaff(null)).thenReturn(List.of(
				staff("TEC-014", "HCM"), staff("TEC-018", "HN")));

		List<StaffDto> result = service.listStaff(null);

		assertEquals("Ho Chi Minh City", result.get(0).branchProvinceName());
		assertEquals("Ha Noi", result.get(1).branchProvinceName());
		verify(referenceService, times(1)).listProvinces();
	}

	@Test
	void listStaffFallsBackToTheRawCodeForAnUnknownProvince() {
		when(smpStaffClient.listStaff(null)).thenReturn(List.of(staff("TEC-099", "ZZ")));

		List<StaffDto> result = service.listStaff(null);

		assertEquals("ZZ", result.get(0).branchProvinceName());
	}

	@Test
	void createStaffTranslatesADuplicateEmailIntoAnApiException() {
		when(smpStaffClient.createStaff(any())).thenThrow(new SmpDuplicateEmailException("dup@fixinh.vn"));

		ApiException ex = assertThrows(ApiException.class, () -> service.createStaff(createCommand()));

		assertEquals(ErrorCode.EMAIL_ALREADY_REGISTERED, ex.getErrorCode());
		verify(notificationService, never()).sendStaffWelcomeEmail(any());
	}

	@Test
	void createStaffFailsWhenTheWelcomeEmailCouldNotBeSent() {
		SmpStaffDto created = staff("TEC-100", "HCM");
		when(smpStaffClient.createStaff(any())).thenReturn(created);
		when(notificationService.sendStaffWelcomeEmail(any()))
				.thenReturn(new StaffWelcomeEmailResult("notif-1", false));

		ApiException ex = assertThrows(ApiException.class, () -> service.createStaff(createCommand()));

		assertEquals(ErrorCode.EMAIL_DELIVERY_FAILED, ex.getErrorCode());
	}

	@Test
	void createStaffSucceedsWhenTheAccountIsMadeAndTheEmailGoesOut() {
		SmpStaffDto created = staff("TEC-100", "HCM");
		when(smpStaffClient.createStaff(any())).thenReturn(created);
		when(notificationService.sendStaffWelcomeEmail(any()))
				.thenReturn(new StaffWelcomeEmailResult("notif-1", true));

		StaffDto result = service.createStaff(createCommand());

		assertEquals("TEC-100", result.staffId());
		assertEquals(Boolean.TRUE, result.notificationSent());
	}

	@Test
	void updateStaffTranslatesAnUnknownIdIntoAnApiException() {
		when(smpStaffClient.updateStaff(any(), any())).thenThrow(new NoSuchElementException("no such staff"));

		ApiException ex = assertThrows(ApiException.class,
				() -> service.updateStaff("TEC-999", updateCommand()));

		assertEquals(ErrorCode.STAFF_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void listCustomersKeepsOnlyTheFirstCopyOfADuplicateProfile() {
		Customer first = customer("cust-1", "First Copy");
		Customer duplicate = customer("cust-1", "Stray Duplicate");
		when(customerService.listAll()).thenReturn(List.of(first, duplicate));
		when(authService.listByRole(Role.CUSTOMER)).thenReturn(List.of());

		List<CustomerDto> result = service.listCustomers();

		assertEquals(1, result.size());
		assertEquals("First Copy", result.get(0).fullName());
	}

	@Test
	void setCustomerStatusCombinesTheCredentialAndTheProfile() {
		UserCredential credential = UserCredential.builder()
				.id("cust-1")
				.phoneNumber("0912345678")
				.accountStatus(AccountStatus.DEACTIVATED)
				.build();
		when(authService.setAccountStatus("cust-1", AccountStatus.DEACTIVATED)).thenReturn(credential);
		when(customerService.findById("cust-1")).thenReturn(Optional.of(customer("cust-1", "Nguyen Van A")));

		CustomerDto result = service.setCustomerStatus("cust-1", AccountStatus.DEACTIVATED);

		assertEquals(AccountStatus.DEACTIVATED, result.accountStatus());
		assertEquals("0912345678", result.phoneNumber());
	}

	@Test
	void setCustomerStatusThrowsWhenTheProfileIsGone() {
		UserCredential credential = UserCredential.builder().id("cust-1").accountStatus(AccountStatus.ACTIVE).build();
		when(authService.setAccountStatus("cust-1", AccountStatus.ACTIVE)).thenReturn(credential);
		when(customerService.findById("cust-1")).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class,
				() -> service.setCustomerStatus("cust-1", AccountStatus.ACTIVE));
		assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
	}

	private SmpStaffDto staff(String staffId, String branchProvinceCode) {
		return new SmpStaffDto(staffId, "Some Name", staffId.toLowerCase() + "@fixinh.vn", "0900000000",
				StaffRole.TECHNICIAN, EmploymentStatus.ACTIVE,
				new SmpAddress("1", "Le Loi", "Phuong Ben Thanh", "HCM"), branchProvinceCode, null);
	}

	private StaffCreateCommand createCommand() {
		return new StaffCreateCommand("New Hire", "new.hire@fixinh.vn", "0900000000",
				StaffRole.TECHNICIAN, new SmpAddress("1", "Le Loi", "Phuong Ben Thanh", "HCM"), "HCM");
	}

	private StaffUpdateCommand updateCommand() {
		return new StaffUpdateCommand("Updated Name", "0900000001",
				new SmpAddress("1", "Le Loi", "Phuong Ben Thanh", "HCM"), EmploymentStatus.INACTIVE);
	}

	private Customer customer(String id, String fullName) {
		return Customer.builder()
				.id(id)
				.email(fullName.toLowerCase().replace(" ", ".") + "@example.com")
				.fullName(fullName)
				.role(Role.CUSTOMER)
				.build();
	}
}
