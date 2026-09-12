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

export function completeAddress(payload) {
  return apiClient.put(API_ENDPOINTS.CUSTOMERS.MY_ADDRESS, payload);
}
