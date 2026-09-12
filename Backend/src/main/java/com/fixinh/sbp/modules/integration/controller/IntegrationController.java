package com.fixinh.sbp.modules.integration.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto;
import com.fixinh.sbp.modules.integration.dto.external.EventReceiptDto;
import com.fixinh.sbp.modules.integration.interfaces.external.IntegrationExternalService;

import lombok.RequiredArgsConstructor;

/**
 * Base path /integration — the one door SMP comes in through (§11, Req 4.M2).
 *
 * <p>No {@code @AuthenticationPrincipal} anywhere here, unlike every other controller. The
 * caller is SMP, established by {@code IntegrationAuthenticationFilter} before this class is
 * reached, and there is nothing about that identity a handler needs: an event names its own
 * customer and its own booking. A principal parameter would be a thing to read and therefore
 * a thing to misread.
 */
@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
public class IntegrationController {

	private final IntegrationExternalService integrationService;

	/**
	 * §11.7. Accepts a booking change, refreshes the projection, and lets the notification
	 * module react — all before returning, so a 202 means the work is done and not merely
	 * queued.
	 *
	 * <p>202 rather than 200 because that is what §11.7 specifies, and it reads correctly from
	 * SMP's side: SBP has taken responsibility for the change, and what SBP then does with it
	 * is not SMP's business.
	 *
	 * <p>No bean validation on the body. {@code BookingEventValidator} owns §11.7's rules so
	 * that a missing field comes back as {@code EVENT_MALFORMED} with a message naming it,
	 * which is what §11.7 asks for — {@code @Valid} would answer the same fault with a generic
	 * field-error list instead.
	 */
	@PostMapping("/booking-events")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public EventReceiptDto receiveBookingEvent(@RequestBody(required = false)
			BookingStatusWebhookDto event) {

		// required = false so an empty body reaches the validator and earns §11.7's own 400
		// rather than Spring's generic one about a missing request body.
		return integrationService.receiveBookingEvent(event);
	}
}