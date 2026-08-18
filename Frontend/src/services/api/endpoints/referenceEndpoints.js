import { apiClient } from '../apiClient'
import { API_ENDPOINTS } from '../../../config/api'

// API Documentation Section 5. Small, slow-changing lists that populate dropdowns.

/**
 * GET /reference/provinces (Section 5.1). Public — the address dropdown has to render
 * before registration has issued anyone a token.
 *
 * @returns {Promise<Array<{provinceCode: string, provinceName: string,
 *                          isMunicipality: boolean, officialCode: string}>>}
 *   `provinceCode` is what registration sends back; `officialCode` is only for looking up
 *   that province's wards against the public address API.
 */
export async function fetchProvinces() {
  const response = await apiClient.get(API_ENDPOINTS.REFERENCE.PROVINCES, { auth: false })
  return response.items
}
