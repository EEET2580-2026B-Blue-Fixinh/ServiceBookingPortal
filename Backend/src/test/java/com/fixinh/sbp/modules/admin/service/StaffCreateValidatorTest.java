package com.fixinh.sbp.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.FieldViolation;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.smp.dto.SmpAddress;
import com.fixinh.sbp.common.smp.dto.StaffRole;
import com.fixinh.sbp.modules.admin.dto.internal.StaffCreateCommand;

class StaffCreateValidatorTest {

	private RegionResolver regionResolver;
	private StaffCreateValidator validator;

	@BeforeEach
	void setUp() {
		regionResolver = mock(RegionResolver.class);
		when(regionResolver.isKnown("HCM")).thenReturn(true);
		validator = new StaffCreateValidator(regionResolver);
	}

	@Test
	void acceptsAFullyValidCommand() {
		assertDoesNotThrow(() -> validator.validate(validCommand()));
	}

	@Test
	void rejectsANameThatIsTooShort() {
		StaffCreateCommand command = withFullName("A");
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("fullName"));
	}

	@Test
	void rejectsAnEmailWithNoAtSymbol() {
		StaffCreateCommand command = withEmail("not-an-email");
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("email"));
	}

	@Test
	void rejectsAnEmailWithNoDotAfterTheAtSymbol() {
		StaffCreateCommand command = withEmail("john@fixinh");
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("email"));
	}

	@Test
	void rejectsAPhoneNumberThatDoesNotStartWithZero() {
		StaffCreateCommand command = withPhoneNumber("19876543");
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("phoneNumber"));
	}

	@Test
	void rejectsAPhoneNumberWithNonDigitCharacters() {
		StaffCreateCommand command = withPhoneNumber("098-765-4321");
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("phoneNumber"));
	}

	@Test
	void rejectsAMissingAddress() {
		StaffCreateCommand command = withAddress(null);
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("address"));
	}

	@Test
	void rejectsAnAddressMissingTheWard() {
		StaffCreateCommand command = withAddress(new SmpAddress("12", "Nguyen Trai", "", "HCM"));
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("address"));
	}

	@Test
	void rejectsAnUnknownProvinceCode() {
		when(regionResolver.isKnown("ZZ")).thenReturn(false);
		StaffCreateCommand command = withAddress(new SmpAddress("12", "Nguyen Trai", "Phuong Ben Thanh", "ZZ"));
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("address.provinceCode"));
	}

	@Test
	void rejectsAMissingBranchProvinceCode() {
		StaffCreateCommand command = withBranchProvinceCode("");
		List<FieldViolation> violations = validationFailure(command);
		assertTrue(fields(violations).contains("branchProvinceCode"));
	}

	@Test
	void reportsEveryBrokenFieldTogetherRatherThanStoppingAtTheFirst() {
		StaffCreateCommand command = new StaffCreateCommand("A", "not-an-email", "19876543",
				StaffRole.TECHNICIAN, null, "");
		List<FieldViolation> violations = validationFailure(command);
		assertEquals(Set.of("fullName", "email", "phoneNumber", "address", "branchProvinceCode"), fields(violations));
	}

	private List<FieldViolation> validationFailure(StaffCreateCommand command) {
		ApiException ex = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
				() -> validator.validate(command));
		return ex.getFieldErrors();
	}

	private Set<String> fields(List<FieldViolation> violations) {
		return violations.stream().map(FieldViolation::field).collect(Collectors.toSet());
	}

	private StaffCreateCommand validCommand() {
		return new StaffCreateCommand("John Tran", "john.tran@fixinh.vn", "0987654321",
				StaffRole.TECHNICIAN, new SmpAddress("12", "Nguyen Trai", "Phuong Ben Thanh", "HCM"), "HCM");
	}

	private StaffCreateCommand withFullName(String fullName) {
		StaffCreateCommand base = validCommand();
		return new StaffCreateCommand(fullName, base.email(), base.phoneNumber(), base.staffRole(),
				base.address(), base.branchProvinceCode());
	}

	private StaffCreateCommand withEmail(String email) {
		StaffCreateCommand base = validCommand();
		return new StaffCreateCommand(base.fullName(), email, base.phoneNumber(), base.staffRole(),
				base.address(), base.branchProvinceCode());
	}

	private StaffCreateCommand withPhoneNumber(String phoneNumber) {
		StaffCreateCommand base = validCommand();
		return new StaffCreateCommand(base.fullName(), base.email(), phoneNumber, base.staffRole(),
				base.address(), base.branchProvinceCode());
	}

	private StaffCreateCommand withAddress(SmpAddress address) {
		StaffCreateCommand base = validCommand();
		return new StaffCreateCommand(base.fullName(), base.email(), base.phoneNumber(), base.staffRole(),
				address, base.branchProvinceCode());
	}

	private StaffCreateCommand withBranchProvinceCode(String branchProvinceCode) {
		StaffCreateCommand base = validCommand();
		return new StaffCreateCommand(base.fullName(), base.email(), base.phoneNumber(), base.staffRole(),
				base.address(), branchProvinceCode);
	}
}
