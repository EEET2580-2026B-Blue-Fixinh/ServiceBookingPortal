import { apiClient } from '../apiClient'
import { API_ENDPOINTS } from '../../../config/api'

export function fetchBookingRating(bookingRef) {
  return apiClient.get(API_ENDPOINTS.BOOKINGS.RATING(bookingRef))
}

export async function fetchMyRatings() {
  const response = await apiClient.get(API_ENDPOINTS.RATINGS.CREATE)
  return response.items
}

export function submitRating({ bookingRef, overallScore, reviewComment, subcategoryScores, evidence = [] }) {
  const form = new FormData()
  form.append('bookingRef', bookingRef)
  form.append('overallScore', String(overallScore))
  if (reviewComment?.trim()) form.append('reviewComment', reviewComment.trim())
  if (subcategoryScores?.length) form.append('subcategoryScores', JSON.stringify(subcategoryScores))
  evidence.forEach((file) => form.append('evidence', file))
  return apiClient.post(API_ENDPOINTS.RATINGS.CREATE, form)
}

export function deleteRating(ratingId) {
  return apiClient.delete(API_ENDPOINTS.RATINGS.DETAIL(ratingId))
}
