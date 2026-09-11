import { useEffect, useRef, useState } from 'react'
import { fetchStaff } from '../services/adminStaffEndpoints'

const SEARCH_DEBOUNCE_MS = 300
const PAGE_SIZE = 5

const STAFF_SORTS = {
  'Name (A–Z)': (a, b) => a.fullName.localeCompare(b.fullName),
  'Name (Z–A)': (a, b) => b.fullName.localeCompare(a.fullName),
  'Status (Active first)': (a, b) =>
    a.employmentStatus === b.employmentStatus
      ? a.fullName.localeCompare(b.fullName)
      : a.employmentStatus === 'ACTIVE'
        ? -1
        : 1,
  'Status (Inactive first)': (a, b) =>
    a.employmentStatus === b.employmentStatus
      ? a.fullName.localeCompare(b.fullName)
      : a.employmentStatus === 'ACTIVE'
        ? 1
        : -1,
  'Role (A–Z)': (a, b) => a.staffRole.localeCompare(b.staffRole) || a.fullName.localeCompare(b.fullName),
  'Branch (A–Z)': (a, b) =>
    (a.branchProvinceName ?? '').localeCompare(b.branchProvinceName ?? '') || a.fullName.localeCompare(b.fullName),
}

export const STAFF_SORT_OPTIONS = Object.keys(STAFF_SORTS)

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
  const [sortBy, setSortBy] = useState(STAFF_SORT_OPTIONS[0])
  // Bumped on every successful reload so the active-search effect below
  // re-runs and refreshes searchResults too - otherwise an edit or create
  // made while a search is active never shows up until the query changes.
  const [refreshKey, setRefreshKey] = useState(0)

  // Discards a search response that resolves after a newer one was already
  // requested, so fast typing can't let a stale result overwrite a fresh one.
  const requestId = useRef(0)

  const reload = () => {
    fetchStaff()
      .then((data) => {
        setAllStaff(data)
        setError('')
        setRefreshKey((key) => key + 1)
      })
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
          if (thisRequest === requestId.current) {
            setSearchResults(data)
            setError('')
          }
        })
        .catch((err) => {
          if (thisRequest === requestId.current) setError(err.message)
        })
        .finally(() => {
          if (thisRequest === requestId.current) setSearching(false)
        })
    }, SEARCH_DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [search, refreshKey])

  const technicianCount = allStaff.filter((m) => m.staffRole === 'TECHNICIAN').length

  // The list the table is paging through: the search response when searching,
  // otherwise the full roster.
  const visibleStaff = search.trim() ? (searchResults ?? allStaff) : allStaff
  const sortedStaff = [...visibleStaff].sort(STAFF_SORTS[sortBy])

  const pageCount = Math.max(1, Math.ceil(sortedStaff.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageStaff = sortedStaff.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  // A new search makes the current page number meaningless, so it sends the
  // reader back to the first page.
  const changeSearch = (value) => {
    setSearch(value)
    setPage(1)
  }

  const changeSort = (value) => {
    setSortBy(value)
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
    sortBy,
    setSortBy: changeSort,
    sortOptions: STAFF_SORT_OPTIONS,
    technicianCount,
    receptionistCount: allStaff.length - technicianCount,
    reload,
  }
}
