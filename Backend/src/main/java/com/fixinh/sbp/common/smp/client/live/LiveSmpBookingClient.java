	package com.fixinh.sbp.common.smp.client.live;

import java.util.List;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.client.LocalCancellationRule;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpBookingCreateRequest;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationRequest;
import com.fixinh.sbp.common.smp.dto.SmpRatingBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Calls Team SMT's service over HTTP (§12.1, §12.5). Active under
 * {@code fixinh.sbp.smp.mode=live}.
 *
 * <p><b>Partly live, deliberately.</b> SMT's API today is the catalogue and
 * {@code POST /bookings}; those two calls go over the wire. Cancellation has no endpoint there
 * at all, so {@link #cancelBooking} answers from {@link LocalCancellationRule} exactly as the
 * mock does — see that class for what it costs. Each method says which of the two it is, so no
 * reader has to guess how much of "live" is live.
 *
 * <p>Authentication is the shared bearer token SMT checks on every path except
 * {@code /actuator/health} — a static secret, not OAuth2. SMT's own README calls it an
 * integration baseline.
 */
@Slf4j
public class LiveSmpBookingClient implements SmpBookingClient {

	private final RestClient http;

	public LiveSmpBookingClient(RestClient http) {
		this.http = http;
	}

	/**
	 * Live. {@code activeOnly=false} because a booking made before a service was retired still
	 * has to render its name (§5.2).
	 */
	@Override
	public List<SmpRepairServiceDto> listRepairServices() {
		try {
			CatalogueResponse response = http.get()
					.uri(uriBuilder -> uriBuilder.path("/repair-services")
							.queryParam("activeOnly", false)
							.build())
					.retrieve()
					.body(CatalogueResponse.class);

			if (response == null || response.items() == null) {
				log.error("SMP returned no catalogue body.");
				throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
			}
			return response.items().stream().map(CatalogueEntry::toDto).toList();
		}
		catch (RestClientResponseException ex) {
			log.error("SMP refused the catalogue request: {} {}",
					ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("SMP could not be reached for the catalogue.", ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	/**
	 * Live. SMT persists the booking and generates the reference before answering, so a call
	 * that fails after the write still leaves a row behind on their side — which is why the
	 * caller's retry-on-duplicate-reference loop is not free in this mode.
	 */
	@Override
	public SmpBookingDto createBooking(SmpBookingCreateRequest request) {
		try {
			CreateBookingResponse response = http.post()
					.uri("/bookings")
					.body(request)
					.retrieve()
					.body(CreateBookingResponse.class);

			if (response == null || response.bookingRef() == null || response.status() == null) {
				log.error("SMP accepted the booking but returned no usable reference.");
				throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
			}
			return new SmpBookingDto(
					response.bookingRef(), response.status(), response.createdAt());
		}
		catch (RestClientResponseException ex) {
			// SMT answers 404 SERVICE_NOT_FOUND for a service that is unknown *or inactive*.
			// SBP has already checked the catalogue by this point, so reaching here means the
			// two replicas disagree — worth saying so rather than reporting a generic outage.
			if (ex.getStatusCode().value() == 404) {
				log.warn("SMP does not have service {} active, but SBP's catalogue does.",
						request.serviceId());
				throw new ApiException(ErrorCode.SERVICE_NOT_FOUND);
			}
			// 400 VALIDATION_FAILED or 422 INVALID_REQUEST: SBP validated the same booking and
			// let it through, so this is contract drift, not customer error. The body is the
			// only thing that says which field, so it goes in the log verbatim.
			log.error("SMP rejected a booking SBP considered valid: {} {}",
					ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("SMP could not be reached to create a booking.", ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	/**
	 * <b>Not live.</b> SMT exposes no cancellation endpoint, so the §12.4 rule is applied here
	 * and SMT's copy of the booking is left untouched — it will still read CREATED there. The
	 * moment they publish the endpoint this method becomes an HTTP call and nothing above it
	 * changes.
	 */
	@Override
	public SmpCancellationDto cancelBooking(
			String bookingRef, String currentStatus, SmpCancellationRequest request) {

		log.warn("Cancelling {} locally: SMP has no cancellation endpoint, so its own copy of "
				+ "this booking stays at {}.", bookingRef, currentStatus);
		return LocalCancellationRule.apply(bookingRef, currentStatus, request.cancelledAt());
	}

	/**
	 * Live, and deliberately so: §12.3 exists because the rating gate — is this caller the
	 * customer, and is the booking COMPLETED — must be answered by the side that owns the
	 * record, not by SBP's own projection of it.
	 *
	 * <p>{@code localProjection} is used for one field only. SMT's booking response carries no
	 * technician assignment (their README lists it as pending), so the technicians on the rating
	 * snapshot can only come from SBP's projection. Every field the gate turns on —
	 * {@code customerId}, {@code status} — comes from SMP.
	 *
	 * <p><b>Known limitation while SMT's flow is incomplete:</b> nothing in their service moves a
	 * booking to COMPLETED, so in live mode a rating submission will be refused with
	 * BOOKING_NOT_COMPLETED every time. That is the honest answer rather than a false pass, and
	 * it resolves itself when they implement completion.
	 */
	@Override
	public SmpRatingBookingDto findBookingForRating(SmpRatingBookingDto localProjection) {
		String bookingRef = localProjection.bookingRef();
		try {
			BookingResponse response = http.get()
					.uri("/bookings/{bookingRef}", bookingRef)
					.retrieve()
					.body(BookingResponse.class);

			if (response == null || response.status() == null) {
				log.error("SMP returned no usable booking for {}.", bookingRef);
				throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
			}
			return new SmpRatingBookingDto(
					response.bookingRef(),
					response.customerId(),
					response.serviceId(),
					response.serviceName(),
					response.status(),
					localProjection.technicians());
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				log.warn("SMP does not know booking {}, which SBP has a projection of.", bookingRef);
				throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
			}
			log.error("SMP refused the booking lookup for {}: {} {}",
					bookingRef, ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
		catch (RestClientException ex) {
			log.error("SMP could not be reached to look up booking {}.", bookingRef, ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}
	}

	/** SMT wraps the catalogue in {@code items}, unlike its single-resource responses. */
	private record CatalogueResponse(List<CatalogueEntry> items) {
	}

	/** The fields of SMT's booking response that the rating gate needs; the rest is ignored. */
	private record BookingResponse(
			String bookingRef, String customerId, String serviceId, String serviceName,
			String status) {
	}

	/**
	 * SMT's catalogue record names the flag {@code isActive}, so that is the JSON key; SBP's
	 * {@link SmpRepairServiceDto} calls it {@code active}. Mapping it here keeps the rename out
	 * of the shared DTO, which the mock and the reference module also use.
	 */
	private record CatalogueEntry(
			String serviceId,
			String serviceName,
			String categoryName,
			long baseServiceFee,
			int standardDurationMinutes,
			boolean isActive) {

		SmpRepairServiceDto toDto() {
			return new SmpRepairServiceDto(serviceId, serviceName, categoryName,
					baseServiceFee, standardDurationMinutes, isActive);
		}
	}

	/** SMT returns more than SBP keeps; the rest of the body is ignored. */
	private record CreateBookingResponse(
			String bookingRef, String status, java.time.Instant createdAt) {
	}
}
