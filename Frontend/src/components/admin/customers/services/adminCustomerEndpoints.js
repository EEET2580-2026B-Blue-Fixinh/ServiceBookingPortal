import { apiClient } from '../../../../services/api/apiClient'
import { API_ENDPOINTS } from '../../../../config/api'

// Admin-only, requires an ADMIN bearer token (API Documentation Section 10.1-10.3).

export async function fetchCustomers() {
  const response = await apiClient.get(API_ENDPOINTS.ADMIN.CUSTOMERS.LIST)
  return response.items
}

export function setCustomerStatus(customerId, status) {
  return apiClient.patch(API_ENDPOINTS.ADMIN.CUSTOMERS.STATUS(customerId), { status })
}
