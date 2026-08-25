import { useEffect, useRef, useState } from 'react'
import { fetchStaff } from '../services/adminStaffEndpoints'

const SEARCH_DEBOUNCE_MS = 300

/**
 * Loads the staff/technician list and delegates search to the backend/mocked
 * SMT (Req 3.5.M2) instead of filtering an already-fetched list here. `notice`
 * is separate from `error`: it carries the outcome of creating a staff
 * account, which can be a warning (account created, welcome email pending)
 * rather than a failure.
 */
export function useStaff() {
  const [allStaff, setAllStaff] = useState([])
  // Last completed search response. Left stale on purpose while the next
  // search is debouncing, so the table doesn't flash empty between keystrokes.
  const [searchResults, setSearchResults] = useState(null)
  const [loading, setLoading] = useState(true)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [search, setSearch] = useState('')

  // Discards a search response that resolves after a newer one was already
  // requested, so fast typing can't let a stale result overwrite a fresh one.
  const requestId = useRef(0)

  const reload = () => {
    fetchStaff()
      .then(setAllStaff)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(reload, [])

  useEffect(() => {
    const trimmed = search.trim()
    if (!trimmed) return

    const thisRequest = ++requestId.current
    const timer = setTimeout(() => {
      setSearching(true)
      fetchStaff(trimmed)
        .then((data) => {
          if (thisRequest === requestId.current) setSearchResults(data)
        })
        .catch((err) => setError(err.message))
        .finally(() => {
          if (thisRequest === requestId.current) setSearching(false)
        })
    }, SEARCH_DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [search])

  const technicianCount = allStaff.filter((m) => m.staffRole === 'TECHNICIAN').length

  return {
    staff: search.trim() ? (searchResults ?? allStaff) : allStaff,
    total: allStaff.length,
    loading,
    searching,
    error,
    notice,
    setNotice,
    search,
    setSearch,
    technicianCount,
    receptionistCount: allStaff.length - technicianCount,
    reload,
  }
}
