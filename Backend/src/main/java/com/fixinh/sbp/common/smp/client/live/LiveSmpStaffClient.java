package com.fixinh.sbp.common.smp.client.live;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;

/**
 * Placeholder for calling the real SMP staff API once SMT publishes one. Not implemented — no
 * task needs {@code fixinh.sbp.smp.mode=live} yet.
 */
public class LiveSmpStaffClient implements SmpStaffClient {

	private static final String NOT_IMPLEMENTED = "Live SMP integration is not implemented yet.";

	@Override
	public List<SmpStaffDto> listStaff(String query) {
		throw new UnsupportedOperationException(NOT_IMPLEMENTED);
	}

	@Override
	public Optional<SmpStaffDto> findStaff(String staffId) {
		throw new UnsupportedOperationException(NOT_IMPLEMENTED);
	}

	@Override
	public SmpStaffDto createStaff(SmpStaffCreateRequest request) {
		throw new UnsupportedOperationException(NOT_IMPLEMENTED);
	}

	@Override
	public SmpStaffDto updateStaff(String staffId, SmpStaffUpdateRequest request) {
		throw new UnsupportedOperationException(NOT_IMPLEMENTED);
	}
}
