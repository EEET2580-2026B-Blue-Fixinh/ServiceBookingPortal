package com.fixinh.sbp.modules.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.notification.dto.external.NotificationListDto;
import com.fixinh.sbp.modules.notification.interfaces.external.NotificationExternalService;

import lombok.RequiredArgsConstructor;

/**
 * Base path /notifications.
 *
 * <p>Every endpoint here acts on the caller named by the token and on nobody else. There is no
 * path or query parameter naming a recipient, which is not an omission: Req A.2.a puts
 * ownership in the {@code sub} claim, and a recipient parameter would be a thing to tamper
 * with.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationExternalService notificationService;

	/**
	 * The caller's notification history, newest first.
	 *
	 * <p>All three parameters are optional and all three are {@code Integer}/{@code Boolean}
	 * rather than primitives, so "not supplied" stays distinguishable from "supplied as zero
	 * or false" all the way into the service, which owns Section 2.6's defaults. Declaring
	 * {@code defaultValue} here instead would put those rules in two places.
	 */
	@GetMapping
	public NotificationListDto list(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(required = false) Boolean unreadOnly,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {

		return notificationService.list(
				principal.id(), principal.role(), unreadOnly, page, size);
	}

	/**
	 * Marks one notification read. 204, and idempotent — a second call on the
	 * same notification is also a 204, because the frontend marks on open and an open can
	 * happen twice.
	 *
	 * <p>404 when the id is unknown <b>or belongs to someone else</b>. The service does not
	 * distinguish them and neither does this method; Section 9.2 chose that over 403 so that
	 * ids cannot be probed for existence.
	 */
	@PatchMapping("/{notificationId}/read")
	public ResponseEntity<Void> markRead(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable String notificationId) {

		notificationService.markRead(principal.id(), principal.role(), notificationId);

		return ResponseEntity.noContent().build();
	}

	/**
	 * Marks every unread notification for the caller read — the endpoint
	 * behind the "Mark all as read" control. Even when there was nothing to mark. Having an 
     * empty bell is not a failed request, and a control that errored on a second press
     * would be worse than one that quietly did nothing.
	 */
	@PatchMapping("/read-all")
	public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {

		notificationService.markAllRead(principal.id(), principal.role());

		return ResponseEntity.noContent().build();
	}
}