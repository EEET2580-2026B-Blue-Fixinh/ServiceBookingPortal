import { useCallback, useEffect, useRef, useState } from 'react'
import { listBookings } from '../services/api/endpoints/bookingEndpoints'

// Owns the signed-in customer's booking list (API Documentation Section 7.2).
//
// Deliberately thin: searching, filtering, sorting, grouping and paging are all the
// server's work (Reqs 3.3.M2-M6), so there is nothing here but "ask, and hold the answer".
// The moment this file starts sorting something, the two sides have two opinions about
// order and the second page of a filtered list stops making sense.

const EMPTY = Object.freeze({ active: [], past: [], page: null })

/**
 * @param {object} filters passed straight to GET /bookings; see listBookings
 * @returns {{active: Array<object>, past: Array<object>, page: object|null,
 *            status: 'loading'|'ready'|'error', error: Error|null, reload: () => void}}
 */
export function useMyBookings(filters) {
  const [reloadToken, setReloadToken] = useState(0)

  // One piece of state holding the answer and the question it answers. Two would let a
  // render see new data beside the old question, which is the flicker where a fresh list
  // is briefly labelled with the previous filter's count.
  const [result, setResult] = useState({ key: null, data: EMPTY, error: null })

  // Serialised rather than used by identity: callers build the filter object inline, so a
  // fresh literal arrives every render and depending on it directly would refetch forever.
  // Both of these are strings, so the effect's dependency check compares what they mean.
  const filterKey = JSON.stringify(filters ?? {})

  // reloadToken joins it so "ask again" is a different question from "the same question",
  // which is what makes the retry button clear an error instead of sitting on it.
  const requestKey = `${reloadToken}|${filterKey}`

  // Typing in the search box fires a request per change, and responses can land out of
  // order -- the last to arrive is not always the one asked for last. Each request carries
  // a number and only the newest is allowed to write.
  const latestRequest = useRef(0)

  useEffect(() => {
    const requestId = latestRequest.current + 1
    latestRequest.current = requestId

    listBookings(JSON.parse(filterKey))
      .then((response) => {
        if (requestId !== latestRequest.current) return
        setResult({
          key: requestKey,
          data: {
            active: response.active ?? [],
            past: response.past ?? [],
            page: response.page ?? null,
          },
          error: null,
        })
      })
      .catch((requestError) => {
        if (requestId !== latestRequest.current) return
        setResult({ key: requestKey, data: EMPTY, error: requestError })
      })
  }, [requestKey, filterKey])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  // Derived, not stored. Setting a status inside the effect body would mean an extra render
  // on every keystroke before the request has even left -- and React flags it for exactly
  // that reason. "Has the answer caught up with the question yet" is a comparison, not state.
  const settled = result.key === requestKey
  const status = !settled ? 'loading' : result.error ? 'error' : 'ready'

  return { ...result.data, status, error: settled ? result.error : null, reload }
}
