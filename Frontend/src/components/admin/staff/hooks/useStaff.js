import { useEffect, useRef, useState } from 'react'
import { fetchStaff } from '../services/adminStaffEndpoints'

const SEARCH_DEBOUNCE_MS = 300
const PAGE_SIZE = 5

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
  const [page, setPage] = useState(1)

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

  // The list the table is paging through: the search response when searching,
  // otherwise the full roster.
  const visibleStaff = search.trim() ? (searchResults ?? allStaff) : allStaff

  const pageCount = Math.max(1, Math.ceil(visibleStaff.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageStaff = visibleStaff.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  // A new search makes the current page number meaningless, so it sends the
  // reader back to the first page.
  const changeSearch = (value) => {
    setSearch(value)
    setPage(1)
  }

  return {
    staff: pageStaff,
    matchCount: visibleStaff.length,
    total: allStaff.length,
    page: currentPage,
    pageSize: PAGE_SIZE,
    setPage,
    loading,
    searching,
    error,
    notice,
    setNotice,
    search,
    setSearch: changeSearch,
    technicianCount,
    receptionistCount: allStaff.length - technicianCount,
    reload,
  }
}
