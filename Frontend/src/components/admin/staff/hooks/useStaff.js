import { useEffect, useMemo, useState } from 'react'
import { fetchStaff } from '../services/adminStaffEndpoints'

/**
 * Loads the staff/technician list and exposes the search-filtered view
 * AdminStaffPage renders (Req 3.5.S1). `notice` is separate from `error`:
 * it carries the outcome of creating a staff account, which can be a
 * warning (account created, welcome email pending) rather than a failure.
 */
export function useStaff() {
  const [staff, setStaff] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [search, setSearch] = useState('')

  const reload = () => {
    fetchStaff()
      .then(setStaff)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(reload, [])

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase()
    if (!needle) return staff
    return staff.filter((member) =>
      [member.fullName, member.email, member.staffId].some((field) =>
        field?.toLowerCase().includes(needle),
      ),
    )
  }, [staff, search])

  const technicianCount = staff.filter((m) => m.staffRole === 'TECHNICIAN').length

  return {
    staff: filtered,
    total: staff.length,
    loading,
    error,
    notice,
    setNotice,
    search,
    setSearch,
    technicianCount,
    receptionistCount: staff.length - technicianCount,
    reload,
  }
}
