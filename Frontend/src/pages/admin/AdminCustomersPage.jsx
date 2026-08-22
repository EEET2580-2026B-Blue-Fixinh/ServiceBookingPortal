import { useEffect, useMemo, useState } from 'react'
import { AdminShell } from '../../components/admin/AdminShell'
import { ConfirmDialog } from '../../components/shared/ConfirmDialog'
import { MaterialIcon } from '../../components/shared/icons'
import { fetchCustomers, setCustomerStatus } from '../../services/api/endpoints/adminEndpoints'
import { fetchProvinces } from '../../services/api/endpoints/referenceEndpoints'

function getInitials(fullName) {
  if (!fullName) return '?'
  const [first, ...rest] = fullName.trim().split(/\s+/)
  const last = rest.at(-1)
  return ((first?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase()
}

function StatCard({ label, value }) {
  return (
    <div className="flex-1 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
      <p className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">{label}</p>
      <p className="text-[26px] font-extrabold text-bk-text">{value}</p>
    </div>
  )
}

function StatusPill({ status }) {
  const isActive = status === 'ACTIVE'
  const className = isActive ? 'bg-bk-green-soft text-bk-green' : 'bg-bk-bg text-bk-muted'

  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 text-[12.5px] font-bold ${className}`}>
      {isActive ? 'Active' : 'Deactivated'}
    </span>
  )
}

export function AdminCustomersPage() {
  const [customers, setCustomers] = useState([])
  const [provinceNames, setProvinceNames] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pendingId, setPendingId] = useState(null)
  const [search, setSearch] = useState('')
  const [confirmTarget, setConfirmTarget] = useState(null)

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

  const handleConfirmToggle = async () => {
    const customer = confirmTarget
    const nextStatus = customer.accountStatus === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE'
    setPendingId(customer.customerId)
    try {
      const updated = await setCustomerStatus(customer.customerId, nextStatus)
      setCustomers((prev) =>
        prev.map((c) => (c.customerId === updated.customerId ? updated : c)),
      )
      setConfirmTarget(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setPendingId(null)
    }
  }

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
  const deactivatedCount = customers.length - activeCount

  return (
    <AdminShell activeNav="customers" breadcrumb="Admin Portal" title="Customer accounts">
      {error && (
        <p className="mb-4 rounded-[9px] border border-bk-red bg-bk-red-soft px-4 py-2.5 text-bk-red" role="alert">
          {error}
        </p>
      )}

      <div className="mb-[18px] flex gap-3.5 max-[700px]:flex-col">
        <StatCard label="Active" value={activeCount} />
        <StatCard label="Deactivated" value={deactivatedCount} />
      </div>

      <div className="mb-[18px] flex items-center gap-3.5 max-[700px]:flex-wrap">
        <div className="flex flex-1 items-center gap-2 rounded-[9px] border border-bk-border bg-bk-surface px-3 focus-within:border-bk-teal focus-within:ring-[3px] focus-within:ring-bk-teal-soft">
          <MaterialIcon name="search" className="shrink-0 text-[19px] text-bk-muted" />
          <input
            type="text"
            placeholder="Search by name, customer ID or email"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="min-w-0 flex-1 bg-transparent py-2.5 font-[inherit] text-bk-text focus:outline-none"
          />
        </div>
        <p className="shrink-0 font-mono text-[12.5px] text-bk-muted">
          {filtered.length} of {customers.length} customers
        </p>
      </div>

      {loading ? (
        <p className="text-bk-muted">Loading customers…</p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-bk-border bg-bk-surface">
          <table className="w-full table-fixed border-collapse text-left text-[14px]">
            <colgroup>
              <col className="w-[46%]" />
              <col className="w-[20%]" />
              <col className="w-[16%]" />
              <col className="w-[18%]" />
            </colgroup>
            <thead>
              <tr className="border-b border-bk-border bg-bk-bg text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">
                <th className="px-[18px] py-3">Customer</th>
                <th className="px-[18px] py-3">City</th>
                <th className="px-[18px] py-3">Status</th>
                <th className="px-[18px] py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((customer) => (
                <tr key={customer.customerId} className="border-b border-bk-border last:border-0">
                  <td className="px-[18px] py-3.5">
                    <div className="flex items-center gap-3">
                      <span className="flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-full border border-bk-border bg-bk-bg text-[13px] font-bold text-bk-text">
                        {getInitials(customer.fullName)}
                      </span>
                      <div className="flex min-w-0 flex-col">
                        <span className="font-bold text-bk-text">{customer.fullName}</span>
                        <span className="truncate text-[13px] text-bk-muted">
                          {customer.email} · {customer.phoneNumber}
                        </span>
                        <span className="font-mono text-[11.5px] text-bk-muted">{customer.customerId}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-[18px] py-3.5 text-bk-text">
                    {provinceNames[customer.address?.provinceCode] ?? customer.address?.provinceCode ?? '—'}
                  </td>
                  <td className="px-[18px] py-3.5">
                    <StatusPill status={customer.accountStatus} />
                  </td>
                  <td className="px-[18px] py-3.5 text-right">
                    <button
                      type="button"
                      disabled={pendingId === customer.customerId}
                      onClick={() => setConfirmTarget(customer)}
                      className={`appearance-none rounded-[9px] border px-3.5 py-2 text-[13px] font-bold disabled:opacity-50 ${
                        customer.accountStatus === 'ACTIVE'
                          ? 'border-bk-border bg-bk-surface text-bk-red hover:border-bk-red hover:bg-bk-red-soft'
                          : 'border-bk-teal bg-bk-teal text-white hover:bg-bk-teal-strong'
                      }`}
                    >
                      {customer.accountStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {filtered.length === 0 && (
            <p className="px-[18px] py-10 text-center text-bk-muted">
              {customers.length === 0 ? 'No customers yet.' : 'No customers match your search.'}
            </p>
          )}
        </div>
      )}

      <ConfirmDialog
        open={confirmTarget != null}
        title={
          confirmTarget?.accountStatus === 'ACTIVE'
            ? 'Deactivate this account?'
            : 'Activate this account?'
        }
        description={
          confirmTarget?.accountStatus === 'ACTIVE'
            ? `${confirmTarget?.fullName} will no longer be able to sign in.`
            : `${confirmTarget?.fullName} will be able to sign in again.`
        }
        confirmLabel={confirmTarget?.accountStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'}
        danger={confirmTarget?.accountStatus === 'ACTIVE'}
        pending={pendingId === confirmTarget?.customerId}
        onConfirm={handleConfirmToggle}
        onCancel={() => setConfirmTarget(null)}
      />
    </AdminShell>
  )
}