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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.dto.SmpBookingCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationRequest;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;

/**
 * The wire contract with Team SMT, pinned. Every body here was copied from a running instance of
 * their service rather than written from their README, because the two differ in the two places
 * that break silently: the catalogue arrives wrapped in {@code items}, and its flag is
 * {@code isActive} where SBP's DTO calls it {@code active}.
 */
class LiveSmpBookingClientTest {

	private static final String BASE_URL = "http://smp.test/api/v1";
	private static final String TOKEN = "test-token";

	private MockRestServiceServer server;
	private LiveSmpBookingClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new LiveSmpBookingClient(builder.build());
	}

	@Test
	@DisplayName("the catalogue is unwrapped from items and isActive becomes active")
	void listRepairServicesMapsSmtsShape() {
		server.expect(requestTo(BASE_URL + "/repair-services?activeOnly=false"))
				.andExpect(method(org.springframework.http.HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
				.andRespond(withSuccess("""
						{"items":[
						  {"serviceId":"SVC-PLUMB-01","serviceName":"Plumbing Repair",
						   "categoryName":"Plumbing","baseServiceFee":500000,
						   "standardDurationMinutes":120,"isActive":true},
						  {"serviceId":"SVC-ELEC-02","serviceName":"Electrical Repair",
						   "categoryName":"Electrical","baseServiceFee":450000,
						   "standardDurationMinutes":90,"isActive":false}
						]}""", MediaType.APPLICATION_JSON));

		List<SmpRepairServiceDto> catalogue = client.listRepairServices();

		assertThat(catalogue).containsExactly(
				new SmpRepairServiceDto("SVC-PLUMB-01", "Plumbing Repair", "Plumbing",
						500_000, 120, true),
				new SmpRepairServiceDto("SVC-ELEC-02", "Electrical Repair", "Electrical",
						450_000, 90, false));
		server.verify();
	}

	@Test
	@DisplayName("a retired service is kept, not dropped, so an old booking still has a name")
	void listRepairServicesKeepsInactiveEntries() {
		server.expect(requestTo(BASE_URL + "/repair-services?activeOnly=false"))
				.andRespond(withSuccess(
						"""
						{"items":[{"serviceId":"SVC-OLD-01","serviceName":"Retired",
						 "categoryName":"Plumbing","baseServiceFee":1,"standardDurationMinutes":15,
						 "isActive":false}]}""",
						MediaType.APPLICATION_JSON));

		assertThat(client.listRepairServices()).singleElement()
				.extracting(SmpRepairServiceDto::active).isEqualTo(false);
	}

	@Test
	@DisplayName("an unreachable catalogue is a 503, never a 500 the customer cannot act on")
	void listRepairServicesTranslatesFailure() {
		server.expect(requestTo(BASE_URL + "/repair-services?activeOnly=false"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.listRepairServices())
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMP_UNAVAILABLE);
	}

	@Test
	@DisplayName("the whole booking is sent, and only the reference is read back")
	void createBookingSendsEveryFieldSmtRequires() {
		server.expect(requestTo(BASE_URL + "/bookings"))
				.andExpect(method(org.springframework.http.HttpMethod.POST))
				.andExpect(jsonPath("$.customerId").value("CUS-001"))
				.andExpect(jsonPath("$.customerFullName").value("Nguyen Hong Anh"))
				.andExpect(jsonPath("$.serviceId").value("SVC-PLUMB-01"))
				.andExpect(jsonPath("$.issueDescription").value("Leaking pipe under the sink."))
				.andExpect(jsonPath("$.preferredServiceDate").value("2026-09-10"))
				.andExpect(jsonPath("$.originChannel").value("SBP"))
				.andExpect(jsonPath("$.serviceAddress.ward").value("Ward 5"))
				.andExpect(jsonPath("$.serviceAddress.provinceName").value("Ho Chi Minh City"))
				.andRespond(withStatus(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"bookingRef":"ORD-2026-9436","status":"CREATED",
								 "serviceName":"Plumbing Repair","preferredServiceDate":"2026-09-10",
								 "createdAt":"2026-09-09T09:02:42.419521Z",
								 "message":"Your booking has been created."}"""));

		SmpBookingDto ack = client.createBooking(sampleRequest());

		assertThat(ack.bookingRef()).isEqualTo("ORD-2026-9436");
		assertThat(ack.status()).isEqualTo("CREATED");
		assertThat(ack.createdAt()).isEqualTo(Instant.parse("2026-09-09T09:02:42.419521Z"));
		server.verify();
	}

	@Test
	@DisplayName("SMP not having the service is SERVICE_NOT_FOUND, not a generic outage")
	void createBookingTranslatesUnknownService() {
		server.expect(requestTo(BASE_URL + "/bookings"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"error":"SERVICE_NOT_FOUND",
								 "message":"Repair service was not found or is inactive."}"""));

		assertThatThrownBy(() -> client.createBooking(sampleRequest()))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERVICE_NOT_FOUND);
	}

	@Test
	@DisplayName("a booking SBP validated and SMP refused is contract drift, reported as 503")
	void createBookingTranslatesRejection() {
		server.expect(requestTo(BASE_URL + "/bookings"))
				.andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"error":"INVALID_REQUEST",
								 "message":"The available time window must be at least 60 minutes."}"""));

		assertThatThrownBy(() -> client.createBooking(sampleRequest()))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMP_UNAVAILABLE);
	}

	@Test
	@DisplayName("cancellation never reaches SMP, because SMP has no endpoint for it")
	void cancelBookingIsAnsweredLocally() {
		SmpCancellationDto ack = client.cancelBooking("ORD-2026-9436", "WORKING",
				SmpCancellationRequest.byCustomer("Changed my mind", List.of(), Instant.EPOCH));

		assertThat(ack.status()).isEqualTo("TERMINATED_BY_CUSTOMER");
		// No expectation was registered above; verify() proves nothing was sent.
		server.verify();
	}

	@Test
	@DisplayName("a finished booking is still refused with the distinguishable 409")
	void cancelBookingRefusesCompleted() {
		assertThatThrownBy(() -> client.cancelBooking("ORD-2026-9436", "COMPLETED",
				SmpCancellationRequest.byCustomer("Too late", List.of(), Instant.EPOCH)))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_NOT_CANCELLABLE);
	}

	private static SmpBookingCreateRequest sampleRequest() {
		return SmpBookingCreateRequest.fromSbp(
				"CUS-001",
				"Nguyen Hong Anh",
				"SVC-PLUMB-01",
				"Leaking pipe under the sink.",
				LocalDate.of(2026, 9, 10),
				LocalTime.of(18, 0),
				LocalTime.of(20, 0),
				new SmpBookingCreateRequest.Address(
						"123", "Green Street", "Ward 5", "SG", "Ho Chi Minh City"));
	}
}
