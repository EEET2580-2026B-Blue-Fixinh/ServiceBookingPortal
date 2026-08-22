package com.fixinh.sbp.modules.notification.dto.external;

import java.util.List;

import com.fixinh.sbp.common.dto.PageResponse;

/**
 * The body of {@code GET /notifications} 
 *
 * @param unreadCount <b>the caller's total unread, not this page's.</b> It does not shrink as
 *        the reader pages forward, and it is unaffected by {@code unreadOnly} — the bell badge
 *        would be wrong on both counts otherwise
 */
public record NotificationListDto(
		List<NotificationDto> items,
		long unreadCount,
		PageResponse.Metadata page) {

	/** Nobody's notifications: an administrator's, or a page past the end of the history. */
	public static NotificationListDto empty(int page, int size) {
		return new NotificationListDto(List.of(), 0L, PageResponse.Metadata.of(page, size, 0L));
	}
}