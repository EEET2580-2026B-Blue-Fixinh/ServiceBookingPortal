import { apiClient } from '../../../../services/api/apiClient'
import { API_ENDPOINTS } from '../../../../config/api'

// Admin-only, requires an ADMIN bearer token (API Documentation Section 10.4-10.7).

export async function fetchStaff(query) {
  const path = query ? `${API_ENDPOINTS.ADMIN.STAFF.LIST}?q=${encodeURIComponent(query)}` : API_ENDPOINTS.ADMIN.STAFF.LIST
  const response = await apiClient.get(path)
  return response.items
}

export function fetchStaffMember(staffId) {
  return apiClient.get(API_ENDPOINTS.ADMIN.STAFF.DETAIL(staffId))
}

export function createStaff(command) {
  return apiClient.post(API_ENDPOINTS.ADMIN.STAFF.CREATE, command)
}

export function updateStaff(staffId, command) {
  return apiClient.put(API_ENDPOINTS.ADMIN.STAFF.UPDATE(staffId), command)
}
