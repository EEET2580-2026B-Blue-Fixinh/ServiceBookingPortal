import { useEffect, useMemo, useState } from 'react'
import { fetchCustomers } from '../services/adminCustomerEndpoints'
import { fetchProvinces } from '../../../../services/api/endpoints/referenceEndpoints'
import { usePagedList } from '../../../../hooks/usePagedList'
import { byName, fieldFirst } from '../../../../utils/sortComparators'

const NAME_SORTS = {
  'Name (A–Z)': byName,
  'Name (Z–A)': (a, b) => byName(b, a),
  'Status (Active first)': fieldFirst('accountStatus', 'ACTIVE'),
  // Matches the "Deactivated" wording on CustomerCard's status pill, not the
  // raw "INACTIVE"-sounding accountStatus value.
  'Status (Deactivated first)': fieldFirst('accountStatus', 'DEACTIVATED'),
}

export const CUSTOMER_SORT_OPTIONS = [...Object.keys(NAME_SORTS), 'City (A–Z)']

/**
 * Loads the customer list and province names, and exposes the search-filtered
 * view AdminCustomersPage renders (Req 3.5.S2).
 */
export function useCustomers() {
  const [customers, setCustomers] = useState([])
  const [provinceNames, setProvinceNames] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [sortBy, setSortBy] = useState(CUSTOMER_SORT_OPTIONS[0])

  const reload = () => {
    fetchCustomers()
      .then((data) => {
        setCustomers(data)
        setError('')
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(reload, [])

  // The failure state's Try again. reload is also the mount effect, where a
  // synchronous setState would trip react-hooks/set-state-in-effect, so it can't
  // show the loading line itself. This event handler can, and it drops the old
  // error first, so a retry that fails again visibly fails again.
  const retry = () => {
    setLoading(true)
    setError('')
    reload()
  }

  useEffect(() => {
    fetchProvinces()
      .then((provinces) =>
        setProvinceNames(Object.fromEntries(provinces.map((p) => [p.provinceCode, p.provinceName]))),
      )
      .catch(() => {})
  }, [])

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase()
    if (!needle) return customers
    return customers.filter((customer) =>
      [customer.fullName, customer.email, customer.customerId].some((field) =>
        field?.toLowerCase().includes(needle),
      ),
    )
  }, [customers, search])

  const cityOf = (customer) => provinceNames[customer.address?.provinceCode] ?? customer.address?.provinceCode ?? ''

  const sorted = useMemo(() => {
    const comparator =
      sortBy === 'City (A–Z)'
        ? (a, b) => cityOf(a).localeCompare(cityOf(b)) || byName(a, b)
        : NAME_SORTS[sortBy]
    return [...filtered].sort(comparator)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtered, sortBy, provinceNames])

  const paged = usePagedList(sorted)

  // A new search or sort makes the current page number meaningless, so it
  // sends the reader back to the first page.
  const changeSearch = (value) => {
    setSearch(value)
    paged.resetPage()
  }

  const changeSort = (value) => {
    setSortBy(value)
    paged.resetPage()
  }

  const activeCount = customers.filter((c) => c.accountStatus === 'ACTIVE').length

  // Patches one row in place - lets useActivateCustomer report a status change
  // back without knowing how (or whether) the list is filtered right now.
  const replaceCustomer = (updated) => {
    setCustomers((prev) => prev.map((c) => (c.customerId === updated.customerId ? updated : c)))
  }

  return {
    customers: paged.pageItems,
    matchCount: filtered.length,
    total: customers.length,
    page: paged.page,
    pageSize: paged.pageSize,
    setPage: paged.setPage,
    pageSizeOptions: paged.pageSizeOptions,
    setPageSize: paged.setPageSize,
    loading,
    error,
    search,
    setSearch: changeSearch,
    sortBy,
    setSortBy: changeSort,
    sortOptions: CUSTOMER_SORT_OPTIONS,
    provinceNames,
    activeCount,
    deactivatedCount: customers.length - activeCount,
    replaceCustomer,
    reload,
    retry,
  }
}
