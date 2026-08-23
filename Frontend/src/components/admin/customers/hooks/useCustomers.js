import { useEffect, useMemo, useState } from 'react'
import { fetchCustomers } from '../services/adminCustomerEndpoints'
import { fetchProvinces } from '../../../../services/api/endpoints/referenceEndpoints'

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

  const activeCount = customers.filter((c) => c.accountStatus === 'ACTIVE').length

  // Patches one row in place - lets useActivateCustomer report a status change
  // back without knowing how (or whether) the list is filtered right now.
  const replaceCustomer = (updated) => {
    setCustomers((prev) => prev.map((c) => (c.customerId === updated.customerId ? updated : c)))
  }

  return {
    customers: filtered,
    total: customers.length,
    loading,
    error,
    search,
    setSearch,
    provinceNames,
    activeCount,
    deactivatedCount: customers.length - activeCount,
    replaceCustomer,
  }
}
