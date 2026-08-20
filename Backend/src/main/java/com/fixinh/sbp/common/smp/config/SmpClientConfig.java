package com.fixinh.sbp.common.smp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.client.live.LiveSmpStaffClient;
import com.fixinh.sbp.common.smp.client.mock.MockSmpStaffClient;

import tools.jackson.databind.ObjectMapper;

/** Wires whichever {@code Smp*Client} beans match {@code fixinh.sbp.smp.mode} (Req 4.S2). */
@Configuration
public class SmpClientConfig {

	@Bean
	@ConditionalOnProperty(prefix = "fixinh.sbp.smp", name = "mode", havingValue = "mock", matchIfMissing = true)
	public SmpStaffClient mockSmpStaffClient(ObjectMapper objectMapper) {
		return new MockSmpStaffClient(objectMapper);
	}

	@Bean
	@ConditionalOnProperty(prefix = "fixinh.sbp.smp", name = "mode", havingValue = "live")
	public SmpStaffClient liveSmpStaffClient() {
		return new LiveSmpStaffClient();
	}
}
