import { apiClient } from '../apiClient'

//admin-only, requires an ADMIN bearer token.

export async function fetchCustomers() {
  const response = await apiClient.get('/admin/customers')
  return response.items
}

export function setCustomerStatus(customerId, status) {
  return apiClient.patch(`/admin/customers/${customerId}/status`, { status })
}

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