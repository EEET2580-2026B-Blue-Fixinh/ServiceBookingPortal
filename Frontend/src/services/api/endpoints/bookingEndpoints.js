import { apiClient } from '../apiClient'
import { API_ENDPOINTS } from '../../../config/api'

// API Documentation Section 7. A booking belongs to whoever's token sends it — the
// customer id is never part of the request body, only read from the Authorization header.

/**
 * POST /bookings (Section 7.1). Creates a service booking for exactly one repair service.
 * The new booking always starts at status "CREATED" (Req 3.3.S2).
 *
 * @param {{serviceId: string, issueDescription: string, preferredServiceDate: string,
 *          availableStartTime: string, availableEndTime: string,
 *          serviceAddress: {streetNumber: string, streetName: string, ward: string,
 *                           provinceCode: string}}} payload
 * @returns {Promise<{bookingRef: string, status: string, serviceName: string,
 *                    preferredServiceDate: string, createdAt: string, message: string}>}
 */
export function createBooking(payload) {
  return apiClient.post(API_ENDPOINTS.BOOKINGS.CREATE, payload)
}

/**
 * GET /bookings (Section 7.2). The signed-in customer's own bookings, already grouped into
 * active and past, already searched, filtered, sorted and paged — every one of those is the
 * server's job (Reqs 3.3.M2-M6), so the caller renders what comes back and does not re-sort.
 *
 * Omitted parameters are omitted from the URL rather than sent empty: `createdFrom=` is a
 * malformed date to the backend, while an absent `createdFrom` is simply no lower bound.
 *
 * @param {{q?: string, serviceIds?: string[], createdFrom?: string, createdTo?: string,
 *          group?: 'ACTIVE'|'PAST', sort?: string, page?: number, size?: number}} [filters]
 * @returns {Promise<{active: Array<object>, past: Array<object>,
 *                    page: {number: number, size: number, totalElements: number,
 *                           totalPages: number}}>}
 */
export function listBookings(filters = {}) {
  const params = new URLSearchParams()

  if (filters.q?.trim()) params.set('q', filters.q.trim())
  // Repeatable, not comma-joined: Section 7.2 restricts to the given service types by
  // repeating the parameter, and Spring binds every occurrence into one list.
  filters.serviceIds?.forEach((serviceId) => params.append('serviceId', serviceId))
  if (filters.createdFrom) params.set('createdFrom', filters.createdFrom)
  if (filters.createdTo) params.set('createdTo', filters.createdTo)
  if (filters.group) params.set('group', filters.group)
  if (filters.sort) params.set('sort', filters.sort)
  if (filters.page != null) params.set('page', String(filters.page))
  if (filters.size != null) params.set('size', String(filters.size))

  const query = params.toString()
  return apiClient.get(
    query ? `${API_ENDPOINTS.BOOKINGS.LIST}?${query}` : API_ENDPOINTS.BOOKINGS.LIST,
  )
}

/**
 * GET /bookings/{bookingRef} (Section 7.3, Reqs 3.3.S4-S6). One booking in full.
 *
 * Absent fields are omitted rather than null: `bill` is missing until the technician issues
 * one, which Section 7.3 says is ordinary and not an error, and `scheduledStartAt` and
 * `technicians` are missing on anything not SCHEDULED.
 *
 * @param {string} bookingRef
 * @returns {Promise<object>}
 */
export function fetchBooking(bookingRef) {
  return apiClient.get(API_ENDPOINTS.BOOKINGS.DETAIL(bookingRef))
}

/**
 * POST /bookings/{bookingRef}/cancellation (Section 7.4, Req 3.3.M1). Cancels or terminates
 * one of the caller's own bookings.
 *
 * Multipart, because the written reason arrives alongside the photographs. The resulting
 * status is not sent and cannot be chosen — Section 12.4 leaves that to whoever owns the
 * record, so it arrives in the response: CANCELLED_BY_CUSTOMER from Created, Confirmed or
 * Scheduled, TERMINATED_BY_CUSTOMER from Working.
 *
 * @param {string} bookingRef
 * @param {{reason: string, evidence?: File[]}} payload up to three JPEG or PNG images
 * @returns {Promise<{bookingRef: string, status: string, reason: string,
 *                    evidenceUrls: string[], cancelledAt: string}>}
 */
export function cancelBooking(bookingRef, { reason, evidence = [] }) {
  const form = new FormData()
  form.append('reason', reason)
  // Appended under one repeated name rather than evidence[0], evidence[1]: that is what
  // binds to the List<MultipartFile> the endpoint declares.
  evidence.forEach((file) => form.append('evidence', file))

  return apiClient.post(API_ENDPOINTS.BOOKINGS.CANCEL(bookingRef), form)
}
