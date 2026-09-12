package com.fixinh.sbp.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * The error vocabulary clients branch on (API Documentation Appendix B and C).
 *
 * <p>Clients branch on these names — never on {@code message}, which is written for people
 * and will be reworded.
 *
 * <p>Two kinds of code live here:
 * <ul>
 *   <li><b>Top-level</b> codes land in {@code ErrorResponse.error} and carry the HTTP status.</li>
 *   <li><b>Field-level</b> codes (Appendix C) land in {@code fieldErrors[].code}. They always roll
 *       up into a 400 VALIDATION_FAILED response, and carry the {@code example} text the API
 *       returns so the frontend never has to invent wording.</li>
 * </ul>
 *
 * <p>Only the codes the auth slice actually raises are defined. Later tasks add their own
 * (booking, rating, integration, ...) as they need them.
 */
@Getter
public enum ErrorCode {

	// --- Top-level: authentication and authorisation ---
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
			"Email, phone number or password is incorrect."),
	AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED,
			"Authentication is required to access this resource."),
	AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,
			"The access token is malformed or its signature does not match."),
	AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,
			"The access token has expired. Please sign in again."),
	AUTH_ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN,
			"This account has been deactivated."),
	AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED,
			"This session was signed out. Please sign in again."),
	AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,
			"This refresh token is unknown or has already been used."),
	AUTH_REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,
			"Your session has expired. Please sign in again."),
	/**
	 * Appendix B: authenticated, but the role or ownership check failed. Raised on the STOMP
	 * channel (§9.3) for a SUBSCRIBE to any destination other than the caller's own queue,
	 * and for any SEND — the channel is outbound-only.
	 */
	ACCESS_DENIED(HttpStatus.FORBIDDEN,
			"You do not have permission to perform this action."),
	OAUTH_CODE_INVALID(HttpStatus.BAD_REQUEST,
			"The OAuth authorization code is invalid or has expired."),
	OAUTH_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY,
			"Google sign-in is temporarily unavailable. Please try again."),

	/**
	 * A request Spring MVC could not dispatch or read at all — the four below are the shapes
	 * a caller can get wrong before any controller runs.
	 *
	 * <p>They exist because {@code GlobalExceptionHandler}'s catch-all would otherwise answer
	 * every one of them with 500 {@code INTERNAL_ERROR}: the handler for {@code Exception} is
	 * consulted ahead of Spring's own resolver, so a mistake in the request would be reported
	 * as a fault on our side and logged as one.
	 *
	 * <p><b>Not in Appendix B.</b> The appendix names no code for a wrong method, a wrong
	 * media type or a path that maps to nothing, and §2.7's status table lists neither 405 nor
	 * 415. Raised as a discrepancy for the appendix to catch up.
	 */
	// --- Top-level: requests that never reach a controller ---
	ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND,
			"No endpoint exists at this path. Check the method and the URL."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED,
			"That method is not allowed on this endpoint. The Allow header lists the ones that are."),
	REQUEST_MALFORMED(HttpStatus.BAD_REQUEST,
			"The request body could not be read as JSON."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
			"This endpoint does not accept that Content-Type."),

	// --- Top-level: validation and conflicts ---
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid."),
	EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "This email is already registered."),
	PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "This phone number is already registered."),

	// --- Top-level: admin (Req 3.5.S2, 3.5.S1) ---
	CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "No customer exists with this id."),
	STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "SMP does not know that staff identifier."),

	/**
	 * Defined so the catalogue is complete, but no limiter is wired yet — login rate limiting
	 * is a deliberate follow-up, not part of this slice.
	 */
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait and try again."),

	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
			"Something went wrong on our side. Quote the traceId when reporting it."),

	// --- Top-level: booking (API Documentation §7.1, Req 3.3.S1–S3) ---
	SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND,
			"The requested repair service is unknown or no longer active."),
	PREFERRED_DATE_IN_PAST(HttpStatus.UNPROCESSABLE_CONTENT,
			"The preferred service date has already passed."),
	TIME_WINDOW_INVALID(HttpStatus.UNPROCESSABLE_CONTENT,
			"The end time must be after the start time, with at least 60 minutes between them."),
	/**
	 * Defined so the catalogue matches the API doc, but the mock SMP client never fails —
	 * genuinely reachable only once a live client can time out or refuse (§2.8).
	 */
	SMP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
			"The booking service is temporarily unavailable. Please try again."),

	// --- Top-level: booking history (API Documentation §7.2, Req 3.3.M2–M6) ---
	INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST,
			"Only createdAt can be used to sort bookings."),
	INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST,
			"createdFrom must not be later than createdTo."),

	// --- Top-level: booking cancellation (API Documentation §7.4, Req 3.3.M1) ---
	/**
	 * Also the answer when the reference belongs to another customer. §7.3 makes the two
	 * indistinguishable on purpose, so a reference cannot be probed for existence.
	 */
	BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND,
			"No booking exists with this reference."),
	BOOKING_NOT_CANCELLABLE(HttpStatus.CONFLICT,
			"This booking is already completed, cancelled or aborted, so it cannot be cancelled."),
	TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST,
			"At most three images may be attached."),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE,
			"Each image must be 5 MB or smaller."),
	UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
			"Only JPEG and PNG images are accepted."),
	UPLOAD_FAILED(HttpStatus.BAD_GATEWAY,
			"The images could not be uploaded, so nothing was changed. Please try again."),

	// --- Top-level: ratings (API Documentation §8) ---
	SCORE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Every rating score must be between 1 and 5."),
	SUBCATEGORY_SET_INCOMPLETE(HttpStatus.BAD_REQUEST,
			"Send every rating category, or omit subcategory scores entirely."),
	RATING_ALREADY_EXISTS(HttpStatus.CONFLICT,
			"This booking already has a rating."),
	BOOKING_NOT_COMPLETED(HttpStatus.CONFLICT,
			"Only a completed booking may be rated."),
	RATING_NOT_FOUND(HttpStatus.NOT_FOUND, "No live rating exists with this identifier."),

	EMAIL_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY,
			"The staff account was created, but the welcome email could not be sent. "
					+ "It will be retried automatically."),

	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "No notification exists with this id."),

	// --- Top-level: integration (Req 4.M2, 4.M3; API Documentation §11) ---
	/**
	 * Every way of not being SMP on an /integration path -- no header, not a bearer, the
	 * wrong value -- gets this one answer, so the response cannot be used to tell them apart.
	 */
	INTEGRATION_FORBIDDEN(HttpStatus.FORBIDDEN,
			"Only the Service Management Portal may call this endpoint."),
	/**
	 * §11.7: a required field is missing or unusable. SMP should not retry the same payload;
	 * unlike a 5xx, nothing about waiting will make it acceptable.
	 */
	EVENT_MALFORMED(HttpStatus.BAD_REQUEST,
			"The booking event is missing a required field or carries a value SBP does not "
					+ "recognise. Correct the payload before sending it again."),

	// --- Field-level: password (Req 3.1.M1) ---
	PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST,
			"Password must be at least 8 characters.", "Fixinh@2026"),
	PASSWORD_MISSING_DIGIT(HttpStatus.BAD_REQUEST,
			"Password must contain at least one number.", "Fixinh@2026"),
	PASSWORD_MISSING_SPECIAL_CHARACTER(HttpStatus.BAD_REQUEST,
			"Password must contain at least one special character such as $ # @ !.", "Fixinh@2026"),
	PASSWORD_MISSING_UPPERCASE(HttpStatus.BAD_REQUEST,
			"Password must contain at least one capital letter.", "Fixinh@2026"),
	PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST,
			"The two passwords do not match.", "Type the same value twice."),

	// --- Field-level: email (Req 3.1.M2) ---
	EMAIL_AT_SYMBOL_INVALID(HttpStatus.BAD_REQUEST,
			"Email must contain exactly one @ symbol.", "an.nguyen@example.com"),
	EMAIL_DOMAIN_INVALID(HttpStatus.BAD_REQUEST,
			"Email must contain a dot after the @ symbol.", "an.nguyen@example.com"),
	EMAIL_TOO_LONG(HttpStatus.BAD_REQUEST,
			"Email must be shorter than 255 characters.", "an.nguyen@example.com"),
	EMAIL_ILLEGAL_CHARACTER(HttpStatus.BAD_REQUEST,
			"Email must not contain spaces or the characters ( ) ; : .", "an.nguyen@example.com"),

	// --- Field-level: phone, name, address (Reqs 3.1.M3, 3.1.S3, 3.1.S4) ---
	PHONE_LENGTH_INVALID(HttpStatus.BAD_REQUEST,
			"Phone number must be between 7 and 11 digits.", "0912345678"),
	PHONE_PREFIX_INVALID(HttpStatus.BAD_REQUEST,
			"Phone number must start with 0.", "0912345678"),
	PHONE_ILLEGAL_CHARACTER(HttpStatus.BAD_REQUEST,
			"Phone number must contain digits only.", "0912345678"),
	NAME_LENGTH_INVALID(HttpStatus.BAD_REQUEST,
			"Full name must be between 2 and 100 characters.", "Nguyen Hong Anh"),
	ADDRESS_INCOMPLETE(HttpStatus.BAD_REQUEST,
			"Address needs street number, street name, ward and city.",
			"123, Green Street, Ward 5, Ho Chi Minh City"),
	PROVINCE_CODE_INVALID(HttpStatus.BAD_REQUEST,
			"Choose a city from the list.", "HCM");

	private final HttpStatus httpStatus;
	private final String message;

	/** Appendix C's "example" column. Null for top-level codes, which have no field to fix. */
	private final String example;

	ErrorCode(HttpStatus httpStatus, String message) {
		this(httpStatus, message, null);
	}

	ErrorCode(HttpStatus httpStatus, String message, String example) {
		this.httpStatus = httpStatus;
		this.message = message;
		this.example = example;
	}
}