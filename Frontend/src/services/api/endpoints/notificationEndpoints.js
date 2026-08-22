import { apiClient } from '../apiClient'
import { API_ENDPOINTS } from '../../../config/api'

// The signed-in caller's own notification
// history — there is no recipient parameter anywhere here, because ownership comes
// from the token's `sub` claim server-side (Req A.2.a) and a recipient argument
// would only be a thing to tamper with.

/**
 * Turns the caller's options into a query string, omitting anything they did not ask for.
 *
 * Section 9.1 gives all three parameters a default, and the backend owns those
 * defaults — its controller takes `Boolean`/`Integer` rather than primitives precisely
 * so "not supplied" stays distinguishable from "supplied as false or zero". Sending
 * `unreadOnly=false&page=0&size=20` would work, but it would copy those rules onto this
 * side of the wire, where they would then have to be kept in step by hand.
 */
function listQuery({ unreadOnly, page, size }) {
  const params = new URLSearchParams()

  // Literal 'true' rather than String(unreadOnly): false is the default, so the only
  // value worth sending is the one that changes the answer.
  if (unreadOnly) params.set('unreadOnly', 'true')
  if (page !== undefined && page !== null) params.set('page', String(page))
  if (size !== undefined && size !== null) params.set('size', String(size))

  const query = params.toString()
  return query ? `?${query}` : ''
}

/**
 * GET /notifications. Newest first.
 *
 * Returns the whole body rather than unwrapping `items` the way fetchProvinces and
 * fetchCustomers do: `unreadCount` is the bell badge and `page` drives paging, so
 * unwrapping here would throw away two thirds of the response and force a second call
 * to get the count back.
 *
 * @param {{unreadOnly?: boolean, page?: number, size?: number}} [options]
 *   All optional. `unreadCount` is the caller's total unread either way — it is not
 *   filtered by `unreadOnly` and does not shrink as the reader pages forward.
 * @returns {Promise<{items: Array<{notificationId: string, type: string, message: string,
 *                                  bookingRef?: string, readStatus: boolean,
 *                                  createdAt: string}>,
 *                    unreadCount: number,
 *                    page: {number: number, size: number,
 *                           totalElements: number, totalPages: number}}>}
 *   `bookingRef` is absent, not null, on a notification that concerns no booking.
 *   `createdAt` is ISO-8601 UTC; converting it for display is the caller's job (Section 2.6).
 */
export function fetchNotifications(options = {}) {
  return apiClient.get(`${API_ENDPOINTS.NOTIFICATIONS.LIST}${listQuery(options)}`)
}

/**
 * PATCH /notifications/{notificationId}/read. Resolves to null on 204.
 *
 * Idempotent, so a second call for the same notification also succeeds — which matters
 * because the UI marks on open and an open can happen twice.
 *
 * @param {string} notificationId
 * @throws {ApiError} 404 `NOTIFICATION_NOT_FOUND` when the id is unknown *or belongs to
 *   someone else*. Section 9.2 makes those the same answer deliberately, so a 404 here is
 *   not evidence that the notification does not exist.
 */
export function markNotificationRead(notificationId) {
  return apiClient.patch(API_ENDPOINTS.NOTIFICATIONS.READ(notificationId))
}

/**
 * PATCH /notifications/read-all. Resolves to null on 204.
 *
 * Never fails for having nothing to do, so the "Mark all as read" control does not need
 * to know whether anything was unread before it was pressed.
 */
export function markAllNotificationsRead() {
  return apiClient.patch(API_ENDPOINTS.NOTIFICATIONS.READ_ALL)
}