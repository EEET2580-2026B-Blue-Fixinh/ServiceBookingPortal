import { apiClient } from '../../../../services/api/apiClient'

// Admin-only, requires an ADMIN bearer token (API Documentation Section 10.4-10.5).

export async function fetchCustomers() {
  const response = await apiClient.get('/admin/customers')
  return response.items
}

export function setCustomerStatus(customerId, status) {
  return apiClient.patch(`/admin/customers/${customerId}/status`, { status })
}
