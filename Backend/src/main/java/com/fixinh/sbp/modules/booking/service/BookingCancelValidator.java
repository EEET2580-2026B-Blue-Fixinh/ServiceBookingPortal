package com.fixinh.sbp.modules.booking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.exception.FieldViolation;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCancelCommand;

/**
 * The rules §7.4 puts on a cancellation request, checked before a single byte is uploaded and
 * before the booking is touched (Req 3.3.M1).
 *
 * <p><b>Two kinds of failure, deliberately not merged.</b> A bad {@code reason} is a field
 * violation and rolls up into 400 VALIDATION_FAILED with the rest, the same contract
 * {@code BookingCreateValidator} follows. A bad file is not: §7.4 gives each its own top-level
 * status — 400 TOO_MANY_IMAGES, 413 FILE_TOO_LARGE, 415 UNSUPPORTED_FILE_TYPE — because they
 * describe the upload rather than a field the customer typed.
 */
@Component
public class BookingCancelValidator {

	private static final int REASON_MIN_LENGTH = 10;
	private static final int REASON_MAX_LENGTH = 500;

	/** §7.4: "Up to 3 images". */
	private static final int MAX_IMAGES = 3;

	/** §7.4: "5 MB each", counted in binary megabytes. */
	private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

	/**
	 * {@code image/jpg} is not a registered media type, but enough clients send it that
	 * rejecting it would fail uploads §7.4 plainly intends to accept. The file is a JPEG either
	 * way; only the label differs.
	 */
	private static final Set<String> ACCEPTED_CONTENT_TYPES =
			Set.of("image/jpeg", "image/jpg", "image/png");

	/**
	 * @throws ApiException 400 VALIDATION_FAILED, 400 TOO_MANY_IMAGES, 413 FILE_TOO_LARGE or
	 *         415 UNSUPPORTED_FILE_TYPE
	 */
	public void validate(BookingCancelCommand command) {
		validateReason(command.reason());
		validateEvidence(command.evidence());
	}

	private void validateReason(String reason) {
		String trimmed = reason == null ? "" : reason.trim();
		if (trimmed.length() >= REASON_MIN_LENGTH && trimmed.length() <= REASON_MAX_LENGTH) {
			return;
		}

		List<FieldViolation> violations = new ArrayList<>();
		violations.add(new FieldViolation("reason", ErrorCode.VALIDATION_FAILED.name(),
				"A cancellation reason must be between " + REASON_MIN_LENGTH + " and "
						+ REASON_MAX_LENGTH + " characters.",
				trimmed.isEmpty() ? "No reason was given." : "Yours is " + trimmed.length() + ".",
				"The leak stopped by itself and I no longer need the visit."));
		throw ApiException.validationFailed(violations);
	}

	/**
	 * Checked before anything is uploaded, so a request that breaks a rule costs no round trip to
	 * the image store — and so §7.4's 502 UPLOAD_FAILED keeps meaning "the store refused", not
	 * "the request was never acceptable".
	 */
	private void validateEvidence(List<MultipartFile> evidence) {
		if (evidence == null || evidence.isEmpty()) {
			return;
		}

		List<MultipartFile> present = evidence.stream().filter(file -> !file.isEmpty()).toList();
		if (present.size() > MAX_IMAGES) {
			throw new ApiException(ErrorCode.TOO_MANY_IMAGES);
		}

		for (MultipartFile file : present) {
			if (file.getSize() > MAX_IMAGE_BYTES) {
				throw new ApiException(ErrorCode.FILE_TOO_LARGE);
			}
			String contentType = file.getContentType();
			if (contentType == null
					|| !ACCEPTED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
				throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
			}
		}
	}

	/**
	 * The files worth storing: an empty part is what a browser sends for a file input nobody
	 * filled in, and storing a zero-byte image would publish a URL that shows nothing.
	 */
	public List<MultipartFile> presentFiles(List<MultipartFile> evidence) {
		if (evidence == null) {
			return List.of();
		}
		return evidence.stream().filter(file -> !file.isEmpty()).toList();
	}
}
