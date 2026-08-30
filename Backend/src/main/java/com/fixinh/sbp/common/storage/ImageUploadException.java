package com.fixinh.sbp.common.storage;

/**
 * An image could not be stored. Mirrors {@code EmailDeliveryException}: the storage tier reports
 * the failure and the caller decides what it means, rather than this package reaching for an
 * HTTP status it has no business choosing.
 *
 * <p>Callers turn this into 502 {@code UPLOAD_FAILED} and record no change (API Documentation
 * §7.4).
 */
public class ImageUploadException extends RuntimeException {

	public ImageUploadException(String message, Throwable cause) {
		super(message, cause);
	}
}
