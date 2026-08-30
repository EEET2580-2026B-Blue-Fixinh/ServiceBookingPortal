package com.fixinh.sbp.modules.booking.dto.internal;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * The cancellation request as it arrives, before any of it has been checked (API Documentation
 * §7.4, Req 3.3.M1).
 *
 * <p>Carries the uploaded files themselves rather than URLs: nothing has been stored yet, and
 * whether anything gets stored depends on validation that has not run. {@code BookingCancelValidator}
 * checks both parts, and only then does the service upload.
 *
 * <p>An internal DTO (Req A.3.2) — it never leaves the module, and {@code MultipartFile} is a
 * transport type that has no business in a response.
 *
 * @param evidence may be null or empty; §7.4 makes the images optional
 */
public record BookingCancelCommand(String reason, List<MultipartFile> evidence) {
}
