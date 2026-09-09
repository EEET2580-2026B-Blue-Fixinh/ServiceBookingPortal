import { useEffect, useMemo, useState } from 'react'
import { fetchCustomers } from '../services/adminCustomerEndpoints'
import { fetchProvinces } from '../../../../services/api/endpoints/referenceEndpoints'

const PAGE_SIZE = 5

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

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageCustomers = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  // A new search makes the current page number meaningless, so it sends the
  // reader back to the first page.
  const changeSearch = (value) => {
    setSearch(value)
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
    provinceNames,
    activeCount,
    deactivatedCount: customers.length - activeCount,
    replaceCustomer,
  }
}
