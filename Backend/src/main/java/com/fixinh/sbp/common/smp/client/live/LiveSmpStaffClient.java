package com.fixinh.sbp.common.smp.client.live;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.client.SmpDuplicateEmailException;
import com.fixinh.sbp.common.smp.client.SmpStaffClient;
import com.fixinh.sbp.common.smp.dto.SmpStaffCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpStaffDto;
import com.fixinh.sbp.common.smp.dto.SmpStaffUpdateRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Calls a staff stand-in over HTTP (docs/smp-staff-contract.md) — MockSMP in this project,
 * or SMT's own service should it ever publish a staff API. Active under
 * {@code fixinh.sbp.smp.staff-mode=live}, against {@code smp.staff-base-url}: deliberately
 * separate from the booking client's {@code smp.base-url}, so flipping staff to live never
 * sends staff calls to SMT's real service, where they would all 404.
 */
@Slf4j
public class LiveSmpStaffClient implements SmpStaffClient {

	private final RestClient http;

	public LiveSmpStaffClient(RestClient http) {
		this.http = http;
	}

	@Override
	public List<SmpStaffDto> listStaff(String query) {
		try {
			ListResponse response = http.get()
					.uri(uriBuilder -> {
						var builder = uriBuilder.path("/staff");
						if (query != null && !query.isBlank()) {
							builder = builder.queryParam("q", query);
						}
						return builder.build();
					})
					.retrieve()
					.body(ListResponse.class);

			if (response == null || response.items() == null) {
				log.error("Staff stand-in returned no list body.");
				throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
			}
			return response.items();
		}
		catch (RestClientResponseException ex) {
			log.error("Staff stand-in refused the list request: {} {}",
					ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("Staff stand-in could not be reached for the list.", ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	@Override
	public Optional<SmpStaffDto> findStaff(String staffId) {
		try {
			return Optional.ofNullable(http.get()
					.uri("/staff/{staffId}", staffId)
					.retrieve()
					.body(SmpStaffDto.class));
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				return Optional.empty();
			}
			log.error("Staff stand-in refused the lookup for {}: {} {}",
					staffId, ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("Staff stand-in could not be reached to look up {}.", staffId, ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	@Override
	public SmpStaffDto createStaff(SmpStaffCreateRequest request) {
		try {
			SmpStaffDto created = http.post()
					.uri("/staff")
					.body(request)
					.retrieve()
					.body(SmpStaffDto.class);

			if (created == null || created.staffId() == null) {
				log.error("Staff stand-in accepted the create but returned no usable staff member.");
				throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
			}
			return created;
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 409) {
				throw new SmpDuplicateEmailException(request.email());
			}
			log.error("Staff stand-in rejected a create SBP considered valid: {} {}",
					ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("Staff stand-in could not be reached to create a staff member.", ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	@Override
	public SmpStaffDto updateStaff(String staffId, SmpStaffUpdateRequest request) {
		try {
			SmpStaffDto updated = http.put()
					.uri("/staff/{staffId}", staffId)
					.body(request)
					.retrieve()
					.body(SmpStaffDto.class);

			if (updated == null) {
				log.error("Staff stand-in accepted the update for {} but returned nothing.", staffId);
				throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
			}
			return updated;
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				throw new NoSuchElementException("No staff member with id " + staffId);
			}
			log.error("Staff stand-in rejected an update SBP considered valid for {}: {} {}",
					staffId, ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("Staff stand-in could not be reached to update {}.", staffId, ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	/** The stand-in wraps a list in {@code items}, matching docs/smp-staff-contract.md. */
	private record ListResponse(List<SmpStaffDto> items) {
	}
}
