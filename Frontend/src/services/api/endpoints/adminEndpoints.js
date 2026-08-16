import { apiClient } from '../apiClient'

//admin-only, requires an ADMIN bearer token.

export async function fetchCustomers() {
  const response = await apiClient.get('/admin/customers')
  return response.items
}

export function setCustomerStatus(customerId, status) {
  return apiClient.patch(`/admin/customers/${customerId}/status`, { status })
}