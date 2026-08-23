import { apiClient } from '../../../../services/api/apiClient'

// Admin-only, requires an ADMIN bearer token (API Documentation Section 10.6-10.7).

export async function fetchStaff() {
  const response = await apiClient.get('/admin/staff')
  return response.items
}

export function fetchStaffMember(staffId) {
  return apiClient.get(`/admin/staff/${staffId}`)
}

export function createStaff(command) {
  return apiClient.post('/admin/staff', command)
}

export function updateStaff(staffId, command) {
  return apiClient.put(`/admin/staff/${staffId}`, command)
}
