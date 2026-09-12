import { API_ENDPOINTS } from "../../../config/api";
import { apiClient } from "../apiClient";

/**
 * GET /customers/me (API Documentation Section 6.1). The signed-in customer's own profile.
 *
 * @returns {Promise<{customerId: string, fullName: string, email: string,
 *   phoneNumber: string, accountStatus: 'ACTIVE'|'DEACTIVATED', registeredAt: string,
 *   authProviders: string[], address: {streetNumber: string, streetName: string,
 *   ward: string, provinceCode: string, provinceName: string} | null}>}
 */
export function fetchMyProfile() {
  return apiClient.get(API_ENDPOINTS.CUSTOMERS.ME);
}

/**
 * PUT /customers/me (API Documentation Section 6.2). Updates full name, phone number and
 * address — email and password are not touched here. Returns the updated profile, in the same
 * shape as {@link fetchMyProfile}.
 *
 * @param {{fullName: string, phoneNumber: string, address: {streetNumber: string,
 *   streetName: string, ward: string, provinceCode: string}}} payload
 */
export function updateMyProfile(payload) {
  return apiClient.put(API_ENDPOINTS.CUSTOMERS.ME, payload);
}

export function completeAddress(payload) {
  return apiClient.put(API_ENDPOINTS.CUSTOMERS.MY_ADDRESS, payload);
}
