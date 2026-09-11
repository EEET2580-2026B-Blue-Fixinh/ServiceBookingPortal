import { useState } from 'react'
import { deleteRating } from '../../../services/api/endpoints/ratingEndpoints'

/**
 * Withdraws the customer's rating (Req 3.4.M3). `onDeleted` runs once the rating
 * is known to be gone, whether this request removed it or something else did.
 */
export function useDeleteRating({ rating, onDeleted }) {
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState(null)

  const remove = async () => {
    // §8.2 only offers deletion when canDelete is set. Checked again here so no
    // caller can send a request the server is bound to refuse.
    if (!rating?.canDelete || deleting) return

    setDeleting(true)
    setDeleteError(null)
    try {
      await deleteRating(rating.ratingId)
      onDeleted()
    } catch (requestError) {
      // §8.3: already deleted, e.g. from another tab. What the customer asked
      // for is already true, so it is reported as done rather than as a failure.
      if (requestError.code === 'RATING_NOT_FOUND') {
        onDeleted()
      } else {
        setDeleteError(requestError)
      }
    } finally {
      setDeleting(false)
    }
  }

  return { remove, deleting, deleteError }
}
