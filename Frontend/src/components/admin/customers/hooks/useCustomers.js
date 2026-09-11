import { useEffect, useMemo, useState } from 'react'
import { fetchCustomers } from '../services/adminCustomerEndpoints'
import { fetchProvinces } from '../../../../services/api/endpoints/referenceEndpoints'

const PAGE_SIZE = 5

const NAME_SORTS = {
  'Name (A–Z)': (a, b) => a.fullName.localeCompare(b.fullName),
  'Name (Z–A)': (a, b) => b.fullName.localeCompare(a.fullName),
  'Status (Active first)': (a, b) =>
    a.accountStatus === b.accountStatus ? a.fullName.localeCompare(b.fullName) : a.accountStatus === 'ACTIVE' ? -1 : 1,
  'Status (Inactive first)': (a, b) =>
    a.accountStatus === b.accountStatus ? a.fullName.localeCompare(b.fullName) : a.accountStatus === 'ACTIVE' ? 1 : -1,
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
  const [page, setPage] = useState(1)
  const [sortBy, setSortBy] = useState(CUSTOMER_SORT_OPTIONS[0])

  useEffect(() => {
    fetchCustomers()
      .then(setCustomers)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))

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
        ? (a, b) => cityOf(a).localeCompare(cityOf(b)) || a.fullName.localeCompare(b.fullName)
        : NAME_SORTS[sortBy]
    return [...filtered].sort(comparator)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtered, sortBy, provinceNames])

  const pageCount = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageCustomers = sorted.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

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

  const activeCount = customers.filter((c) => c.accountStatus === 'ACTIVE').length

  // Patches one row in place - lets useActivateCustomer report a status change
  // back without knowing how (or whether) the list is filtered right now.
  const replaceCustomer = (updated) => {
    setCustomers((prev) => prev.map((c) => (c.customerId === updated.customerId ? updated : c)))
  }

  return {
    customers: pageCustomers,
    matchCount: filtered.length,
    total: customers.length,
    page: currentPage,
    pageSize: PAGE_SIZE,
    setPage,
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
  }
}
