package com.fixinh.mocksmp.staff;

import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "mocksmp.service-token=test-token")
class StaffControllerTest {

	private static final String AUTH = "Bearer test-token";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsARequestWithNoBearerToken() throws Exception {
		mockMvc.perform(get("/staff")).andExpect(status().isForbidden());
	}

	@Test
	void rejectsARequestWithTheWrongToken() throws Exception {
		mockMvc.perform(get("/staff").header("Authorization", "Bearer wrong"))
				.andExpect(status().isForbidden());
	}

	@Test
	void listsTheSeededStaff() throws Exception {
		mockMvc.perform(get("/staff").header("Authorization", AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
				.andExpect(jsonPath("$.items[0].initialPassword").doesNotExist());
	}

	@Test
	void searchesByNameStaffIdOrEmail() throws Exception {
		mockMvc.perform(get("/staff").param("q", "TEC-014").header("Authorization", AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].staffId").value("TEC-014"));
	}

	@Test
	void returnsOneStaffMemberByIdWithItsAddress() throws Exception {
		mockMvc.perform(get("/staff/TEC-014").header("Authorization", AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("John Tran"))
				.andExpect(jsonPath("$.address.provinceCode").value("HCM"));
	}

	@Test
	void returns404ForAnUnknownStaffId() throws Exception {
		mockMvc.perform(get("/staff/TEC-999").header("Authorization", AUTH))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("STAFF_NOT_FOUND"));
	}

	@Test
	void createsAStaffMemberWithAnInitialPassword() throws Exception {
		mockMvc.perform(post("/staff")
						.header("Authorization", AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "New Hire",
								  "email": "new.hire@fixinh.vn",
								  "phoneNumber": "0900000000",
								  "staffRole": "TECHNICIAN",
								  "address": {
								    "streetNumber": "1",
								    "streetName": "Le Loi",
								    "ward": "Phuong Ben Thanh",
								    "provinceCode": "HCM"
								  },
								  "branchProvinceCode": "HCM"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.staffId", org.hamcrest.Matchers.startsWith("TEC-")))
				.andExpect(jsonPath("$.initialPassword", greaterThan("")));
	}

	@Test
	void rejectsADuplicateEmailOnCreate() throws Exception {
		String body = """
				{
				  "fullName": "Duplicate",
				  "email": "john.tran@fixinh.vn",
				  "phoneNumber": "0900000000",
				  "staffRole": "TECHNICIAN",
				  "address": {
				    "streetNumber": "1",
				    "streetName": "Le Loi",
				    "ward": "Phuong Ben Thanh",
				    "provinceCode": "HCM"
				  },
				  "branchProvinceCode": "HCM"
				}
				""";
		mockMvc.perform(post("/staff")
						.header("Authorization", AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void updatesAStaffMemberAndClearsTheInitialPassword() throws Exception {
		mockMvc.perform(put("/staff/TEC-021")
						.header("Authorization", AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Updated Name",
								  "phoneNumber": "0911111111",
								  "address": {
								    "streetNumber": "2",
								    "streetName": "Le Loi",
								    "ward": "Phuong Ben Thanh",
								    "provinceCode": "HCM"
								  },
								  "employmentStatus": "INACTIVE"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Updated Name"))
				.andExpect(jsonPath("$.employmentStatus").value("INACTIVE"))
				.andExpect(jsonPath("$.initialPassword").doesNotExist());
	}

	@Test
	void returns404WhenUpdatingAnUnknownStaffId() throws Exception {
		mockMvc.perform(put("/staff/TEC-999")
						.header("Authorization", AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Nobody",
								  "phoneNumber": "0900000000",
								  "address": {
								    "streetNumber": "1",
								    "streetName": "Le Loi",
								    "ward": "Phuong Ben Thanh",
								    "provinceCode": "HCM"
								  }
								}
								"""))
				.andExpect(status().isNotFound());
	}
}
