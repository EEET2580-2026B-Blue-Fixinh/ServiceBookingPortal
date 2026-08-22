import { API_ENDPOINTS } from "../../../config/api";
import { apiClient } from "../apiClient";

export function completeAddress(payload) {
  return apiClient.put(API_ENDPOINTS.CUSTOMERS.MY_ADDRESS, payload);
}
