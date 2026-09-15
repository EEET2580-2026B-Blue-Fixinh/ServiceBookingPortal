package com.fixinh.sbp.common.smp.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.client.live.LiveSmpBookingClient;
import com.fixinh.sbp.common.smp.client.live.LiveSmpStaffClient;
import com.fixinh.sbp.common.smp.client.mock.MockSmpBookingClient;
import com.fixinh.sbp.common.smp.client.mock.MockSmpStaffClient;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Which SMP clients the two switches produce — {@link SmpClientConfig} and its properties
 * alone, with no database and no web layer.
 *
 * <p>The first case is the one that matters: bookings live, staff mocked. That is how SBP runs
 * against SMT's service today, and the only way an administrator there can create a
 * receptionist or technician — and so trigger the welcome email of Req 3.1.S5 — at all.
 */
class SmpClientConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfig.class, SmpClientConfig.class)
			.withBean(ObjectMapper.class, JsonMapper::new)
			// Read only when bookings are live, where the live client refuses to start without
			// them; ignored otherwise.
			.withPropertyValues(
					"fixinh.sbp.smp.base-url=http://localhost:9443/api/v1",
					"fixinh.sbp.smp.service-token=test-token");

	@Test
	void staffStayMockedWhenBookingsGoLive() {
		runner.withPropertyValues("fixinh.sbp.smp.mode=live").run(context -> {
			assertInstanceOf(LiveSmpBookingClient.class, context.getBean(SmpBookingClient.class));
			assertInstanceOf(MockSmpStaffClient.class, context.getBean(SmpStaffClient.class),
					"SMT has no staff API yet, so live bookings must not take staff with them");
		});
	}

	@Test
	void staffGoLiveOnlyWhenTheirOwnSwitchSaysSo() {
		runner.withPropertyValues(
						"fixinh.sbp.smp.mode=live",
						"fixinh.sbp.smp.staff-mode=live",
						"fixinh.sbp.smp.staff-base-url=http://localhost:8090",
						"fixinh.sbp.smp.staff-service-token=test-staff-token")
				.run(context -> assertInstanceOf(LiveSmpStaffClient.class,
						context.getBean(SmpStaffClient.class)));
	}

	@Test
	void liveStaffRefusesToStartWithoutItsOwnBaseUrl() {
		runner.withPropertyValues("fixinh.sbp.smp.staff-mode=live",
						"fixinh.sbp.smp.staff-service-token=test-staff-token")
				.run(context -> org.assertj.core.api.Assertions.assertThat(context)
						.hasFailed());
	}

	@Test
	void everythingIsMockedWhenNothingIsSet() {
		runner.run(context -> {
			assertInstanceOf(MockSmpBookingClient.class, context.getBean(SmpBookingClient.class));
			assertInstanceOf(MockSmpStaffClient.class, context.getBean(SmpStaffClient.class));
		});
	}

	/** Binds {@code fixinh.sbp.*} the way {@code @ConfigurationPropertiesScan} does in the app. */
	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SbpProperties.class)
	static class PropertiesConfig {
	}
}