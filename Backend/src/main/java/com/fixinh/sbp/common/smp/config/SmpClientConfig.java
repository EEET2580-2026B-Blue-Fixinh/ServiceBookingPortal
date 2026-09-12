package com.fixinh.sbp.common.smp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.client.live.LiveSmpBookingClient;
import com.fixinh.sbp.common.smp.client.live.LiveSmpStaffClient;
import com.fixinh.sbp.common.smp.client.mock.MockSmpBookingClient;
import com.fixinh.sbp.common.smp.client.mock.MockSmpStaffClient;

import tools.jackson.databind.ObjectMapper;

/**
 * Wires whichever {@code Smp*Client} beans match the configured modes (Req 4.S2): bookings by
 * {@code fixinh.sbp.smp.mode}, staff by {@code fixinh.sbp.smp.staff-mode}.
 */
@Configuration
public class SmpClientConfig {

	/**
	 * Staff are keyed to {@code staff-mode}, not {@code mode}. SMT publishes no staff API yet, so
	 * the mock has to stand in even when bookings are live — see {@code SbpProperties.Smp}.
	 */
	@Bean
	@ConditionalOnProperty(prefix = "fixinh.sbp.smp", name = "staff-mode", havingValue = "mock",
			matchIfMissing = true)
	public SmpStaffClient mockSmpStaffClient(ObjectMapper objectMapper) {
		return new MockSmpStaffClient(objectMapper);
	}

	@Bean
	@ConditionalOnProperty(prefix = "fixinh.sbp.smp", name = "staff-mode", havingValue = "live")
	public SmpStaffClient liveSmpStaffClient() {
		return new LiveSmpStaffClient();
	}

	@Bean
	@ConditionalOnProperty(prefix = "fixinh.sbp.smp", name = "mode", havingValue = "mock", matchIfMissing = true)
	public SmpBookingClient mockSmpBookingClient() {
		return new MockSmpBookingClient();
	}

	@Bean
	@ConditionalOnProperty(prefix = "fixinh.sbp.smp", name = "mode", havingValue = "live")
	public SmpBookingClient liveSmpBookingClient(SbpProperties properties) {
		return new LiveSmpBookingClient(smpRestClient(properties.getSmp()));
	}

	/**
	 * One client per SMP-facing bean rather than a shared one, so the bearer token is attached
	 * by construction and no call site can forget it. SMT checks the token on every path except
	 * {@code /actuator/health}, and answers 403 — not 401 — when it is absent.
	 */
	private static RestClient smpRestClient(SbpProperties.Smp smp) {
		String baseUrl = smp.getBaseUrl();
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException(
					"fixinh.sbp.smp.base-url must be set when smp.mode is live.");
		}
		String token = smp.getServiceToken();
		if (token == null || token.isBlank()) {
			throw new IllegalStateException(
					"fixinh.sbp.smp.service-token must be set when smp.mode is live.");
		}
		return RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.build();
	}
}
