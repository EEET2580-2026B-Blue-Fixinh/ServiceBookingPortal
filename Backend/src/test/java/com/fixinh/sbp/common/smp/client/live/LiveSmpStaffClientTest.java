package com.fixinh.sbp.common.smp.client.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.dto.SmpAddress;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;
import com.fixinh.sbp.common.smp.dto.StaffRole;

/** The wire contract with a staff stand-in (docs/smp-staff-contract.md), pinned. */
class LiveSmpStaffClientTest {

	private static final String BASE_URL = "http://mocksmp.test";
	private static final String TOKEN = "test-staff-token";

	private MockRestServiceServer server;
	private LiveSmpStaffClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new LiveSmpStaffClient(builder.build());
	}

	@Test
	@DisplayName("the list is unwrapped from items")
	void listStaffUnwrapsItems() {
		server.expect(requestTo(BASE_URL + "/staff"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
				.andRespond(withSuccess("""
						{"items":[%s]}""".formatted(staffJson("TEC-014", "John Tran")),
						MediaType.APPLICATION_JSON));

		List<SmpStaffDto> staff = client.listStaff(null);

		assertThat(staff).singleElement().extracting(SmpStaffDto::staffId).isEqualTo("TEC-014");
		server.verify();
	}

	@Test
	@DisplayName("a blank query omits q entirely")
	void listStaffOmitsQForABlankQuery() {
		server.expect(requestTo(BASE_URL + "/staff"))
				.andRespond(withSuccess("""
						{"items":[]}""", MediaType.APPLICATION_JSON));

		client.listStaff("   ");
		server.verify();
	}

	@Test
	@DisplayName("a query containing { and } is sent as data, not parsed as a URI template")
	void listStaffEncodesBraces() {
		server.expect(requestTo(BASE_URL + "/staff?q=%7Babc%7D"))
				.andRespond(withSuccess("""
						{"items":[]}""", MediaType.APPLICATION_JSON));

		client.listStaff("{abc}");
		server.verify();
	}

	@Test
	@DisplayName("a literal + in a query survives instead of decoding to a space at the far end")
	void listStaffEncodesPlus() {
		server.expect(requestTo(BASE_URL + "/staff?q=a%2Bb"))
				.andRespond(withSuccess("""
						{"items":[]}""", MediaType.APPLICATION_JSON));

		client.listStaff("a+b");
		server.verify();
	}

	@Test
	@DisplayName("a refused list request is 503, not a raw 500")
	void listStaffTranslatesFailure() {
		server.expect(requestTo(BASE_URL + "/staff"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.listStaff(null))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMP_UNAVAILABLE);
	}

	@Test
	@DisplayName("an unknown staffId is empty, not an exception")
	void findStaffReturnsEmptyOn404() {
		server.expect(requestTo(BASE_URL + "/staff/TEC-999"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(client.findStaff("TEC-999")).isEmpty();
	}

	@Test
	@DisplayName("a known staffId returns the record")
	void findStaffReturnsTheRecord() {
		server.expect(requestTo(BASE_URL + "/staff/TEC-014"))
				.andRespond(withSuccess(staffJson("TEC-014", "John Tran"), MediaType.APPLICATION_JSON));

		Optional<SmpStaffDto> found = client.findStaff("TEC-014");

		assertThat(found).isPresent();
		assertThat(found.get().fullName()).isEqualTo("John Tran");
	}

	@Test
	@DisplayName("a non-404 failure on lookup is still 503")
	void findStaffTranslatesOtherFailures() {
		server.expect(requestTo(BASE_URL + "/staff/TEC-014"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.findStaff("TEC-014"))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMP_UNAVAILABLE);
	}

	@Test
	@DisplayName("create sends every field and reads the new staff member back")
	void createStaffSendsTheWholeRequest() {
		server.expect(requestTo(BASE_URL + "/staff"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.fullName").value("New Hire"))
				.andExpect(jsonPath("$.email").value("new.hire@fixinh.vn"))
				.andExpect(jsonPath("$.staffRole").value("TECHNICIAN"))
				.andExpect(jsonPath("$.branchProvinceCode").value("HCM"))
				.andRespond(withStatus(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.body(staffJson("TEC-100", "New Hire")));

		SmpStaffDto created = client.createStaff(createRequest());

		assertThat(created.staffId()).isEqualTo("TEC-100");
		server.verify();
	}

	@Test
	@DisplayName("a duplicate email is a distinguishable exception, not a generic outage")
	void createStaffTranslatesDuplicateEmail() {
		server.expect(requestTo(BASE_URL + "/staff"))
				.andRespond(withStatus(HttpStatus.CONFLICT)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"error":"EMAIL_ALREADY_REGISTERED",
								 "message":"A staff account already exists with email new.hire@fixinh.vn"}"""));

		assertThatThrownBy(() -> client.createStaff(createRequest()))
				.isInstanceOf(SmpDuplicateEmailException.class);
	}

	@Test
	@DisplayName("a create SBP considered valid but the stand-in refused is 503")
	void createStaffTranslatesOtherFailures() {
		server.expect(requestTo(BASE_URL + "/staff"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> client.createStaff(createRequest()))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMP_UNAVAILABLE);
	}

	@Test
	@DisplayName("update sends the editable fields and clears the initial password")
	void updateStaffSendsTheEditableFields() {
		server.expect(requestTo(BASE_URL + "/staff/TEC-014"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(jsonPath("$.fullName").value("Updated Name"))
				.andRespond(withSuccess("""
						{"staffId":"TEC-014","fullName":"Updated Name","email":"john.tran@fixinh.vn",
						 "phoneNumber":"0900000001","staffRole":"TECHNICIAN","employmentStatus":"INACTIVE",
						 "address":{"streetNumber":"2","streetName":"Le Loi","ward":"Phuong Ben Thanh",
						 "provinceCode":"HCM"},"branchProvinceCode":"HCM","initialPassword":null}""",
						MediaType.APPLICATION_JSON));

		SmpStaffDto updated = client.updateStaff("TEC-014", updateRequest());

		assertThat(updated.fullName()).isEqualTo("Updated Name");
		assertThat(updated.initialPassword()).isNull();
		server.verify();
	}

	@Test
	@DisplayName("updating an unknown staffId is NoSuchElementException, matching the mock client")
	void updateStaffTranslates404() {
		server.expect(requestTo(BASE_URL + "/staff/TEC-999"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> client.updateStaff("TEC-999", updateRequest()))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	@DisplayName("a non-404 failure on update is still 503")
	void updateStaffTranslatesOtherFailures() {
		server.expect(requestTo(BASE_URL + "/staff/TEC-014"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.updateStaff("TEC-014", updateRequest()))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMP_UNAVAILABLE);
	}

	private static String staffJson(String staffId, String fullName) {
		return """
				{"staffId":"%s","fullName":"%s","email":"john.tran@fixinh.vn",
				 "phoneNumber":"0987654321","staffRole":"TECHNICIAN","employmentStatus":"ACTIVE",
				 "address":{"streetNumber":"12","streetName":"Nguyen Trai","ward":"Phuong Ben Thanh",
				 "provinceCode":"HCM"},"branchProvinceCode":"HCM","initialPassword":"Abc12345"}"""
				.formatted(staffId, fullName);
	}

	private static SmpStaffCreateRequest createRequest() {
		return new SmpStaffCreateRequest("New Hire", "new.hire@fixinh.vn", "0900000000",
				StaffRole.TECHNICIAN, new SmpAddress("1", "Le Loi", "Phuong Ben Thanh", "HCM"), "HCM");
	}

	private static SmpStaffUpdateRequest updateRequest() {
		return new SmpStaffUpdateRequest("Updated Name", "0900000001",
				new SmpAddress("2", "Le Loi", "Phuong Ben Thanh", "HCM"), null);
	}
}
