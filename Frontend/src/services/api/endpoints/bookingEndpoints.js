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
