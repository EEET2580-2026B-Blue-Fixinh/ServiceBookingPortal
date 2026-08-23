import { useEffect, useMemo, useState } from 'react'
import { AdminShell } from '../../components/admin/AdminShell'
import { StaffFormDialog } from '../../components/admin/StaffFormDialog'
import { MaterialIcon } from '../../components/shared/icons'
import {
  createStaff,
  fetchStaff,
  fetchStaffMember,
  updateStaff,
} from '../../services/api/endpoints/adminEndpoints'

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

function RolePill({ role }) {
  const isTechnician = role === 'TECHNICIAN'
  const className = isTechnician ? 'bg-bk-blue-soft text-bk-blue' : 'bg-bk-teal-soft text-bk-teal-strong'
  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 text-[12.5px] font-bold ${className}`}>
      {isTechnician ? 'Technician' : 'Receptionist'}
    </span>
  )
}

function StatusPill({ status }) {
  const isActive = status === 'ACTIVE'
  const className = isActive ? 'bg-bk-green-soft text-bk-green' : 'bg-bk-bg text-bk-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 text-[12.5px] font-bold ${className}`}>
      {isActive ? 'Active' : 'Inactive'}
    </span>
  )
}

export function AdminStaffPage() {
  const [staff, setStaff] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // Distinct from `error` above: that one is for "the list failed to load".
  // This is feedback about the outcome of creating a staff account, which can be
  // a warning (account created, email pending) rather than a failure.
  const [notice, setNotice] = useState(null)
  const [search, setSearch] = useState('')

  const [dialogMode, setDialogMode] = useState(null)
  const [dialogInitialValues, setDialogInitialValues] = useState(null)
  const [dialogPending, setDialogPending] = useState(false)
  const [dialogError, setDialogError] = useState('')
  const [loadingStaffId, setLoadingStaffId] = useState(null)

  const loadStaff = () => {
    fetchStaff()
      .then(setStaff)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(loadStaff, [])

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
  const receptionistCount = staff.length - technicianCount

  const openCreateDialog = () => {
    setDialogError('')
    setNotice(null)
    setDialogInitialValues(null)
    setDialogMode('create')
  }

  const openEditDialog = async (member) => {
    setDialogError('')
    setLoadingStaffId(member.staffId)
    try {
      const detail = await fetchStaffMember(member.staffId)
      setDialogInitialValues(detail)
      setDialogMode('edit')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingStaffId(null)
    }
  }

  const closeDialog = () => {
    setDialogMode(null)
    setDialogInitialValues(null)
    setDialogError('')
  }

  const handleSubmit = async (payload) => {
    const isCreate = dialogMode === 'create'
    setDialogPending(true)
    setDialogError('')
    try {
      if (isCreate) {
        await createStaff(payload)
        setNotice({ variant: 'success', message: 'Staff account created. Welcome email sent.' })
      } else {
        await updateStaff(dialogInitialValues.staffId, payload)
      }
      closeDialog()
      loadStaff()
    } catch (err) {
      // API Documentation §10.6: a 502 EMAIL_DELIVERY_FAILED means the staff
      // account already exists in SMP — only the welcome email failed, and it's
      // being retried in the background. Treating it like any other failure
      // would leave the dialog open, and a retried submit would create a
      // second staff account for the same person.
      if (isCreate && err.code === 'EMAIL_DELIVERY_FAILED') {
        setNotice({
          variant: 'warning',
          message: "Account created. Welcome email hasn't gone out yet; we'll keep retrying.",
        })
        closeDialog()
        loadStaff()
      } else {
        setDialogError(err.message)
      }
    } finally {
      setDialogPending(false)
    }
  }

  return (
    <AdminShell
      activeNav="staff"
      breadcrumb="Admin Portal"
      title="Staff & technicians"
      headerActions={
        <button
          type="button"
          onClick={openCreateDialog}
          className="flex h-[38px] appearance-none items-center gap-1.5 rounded-[9px] bg-bk-teal px-4 text-sm font-bold text-white hover:bg-bk-teal-strong"
        >
          <MaterialIcon name="person_add" className="text-[19px]" />
          New staff account
        </button>
      }
    >
      {error && (
        <p className="mb-4 rounded-[9px] border border-bk-red bg-bk-red-soft px-4 py-2.5 text-bk-red" role="alert">
          {error}
        </p>
      )}

      {notice && (
        <p
          className={
            notice.variant === 'success'
              ? 'mb-4 rounded-[9px] border border-bk-green bg-bk-green-soft px-4 py-2.5 text-bk-green'
              : 'mb-4 rounded-[9px] border border-bk-amber bg-bk-amber-soft px-4 py-2.5 text-bk-amber'
          }
          role={notice.variant === 'success' ? 'status' : 'alert'}
          aria-live={notice.variant === 'success' ? 'polite' : 'assertive'}
        >
          {notice.message}
        </p>
      )}

      <div className="mb-[18px] flex gap-3.5 max-[700px]:flex-col">
        <StatCard label="Technicians" value={technicianCount} />
        <StatCard label="Receptionists" value={receptionistCount} />
      </div>

      <div className="mb-[18px] flex items-center gap-3.5 max-[700px]:flex-wrap">
        <div className="flex flex-1 items-center gap-2 rounded-[9px] border border-bk-border bg-bk-surface px-3 focus-within:border-bk-teal focus-within:ring-[3px] focus-within:ring-bk-teal-soft">
          <MaterialIcon name="search" className="shrink-0 text-[19px] text-bk-muted" />
          <input
            type="text"
            placeholder="Search by name, staff ID or email"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="min-w-0 flex-1 bg-transparent py-2.5 font-[inherit] text-bk-text focus:outline-none"
          />
        </div>
        <p className="shrink-0 font-mono text-[12.5px] text-bk-muted">
          {filtered.length} of {staff.length} staff accounts
        </p>
      </div>

      {loading ? (
        <p className="text-bk-muted">Loading staff…</p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-bk-border bg-bk-surface">
          <table className="w-full table-fixed border-collapse text-left text-[14px]">
            <colgroup>
              <col className="w-[42%]" />
              <col className="w-[16%]" />
              <col className="w-[22%]" />
              <col className="w-[10%]" />
              <col className="w-[10%]" />
            </colgroup>
            <thead>
              <tr className="border-b border-bk-border bg-bk-bg text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">
                <th className="px-[18px] py-3">Staff member</th>
                <th className="px-[18px] py-3">Role</th>
                <th className="px-[18px] py-3">Branch</th>
                <th className="px-[18px] py-3">Status</th>
                <th className="px-[18px] py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((member) => (
                <tr key={member.staffId} className="border-b border-bk-border last:border-0">
                  <td className="px-[18px] py-3.5">
                    <div className="flex items-center gap-3">
                      <span className="flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-full border border-bk-border bg-bk-bg text-[13px] font-bold text-bk-text">
                        {getInitials(member.fullName)}
                      </span>
                      <div className="flex min-w-0 flex-col">
                        <span className="font-bold text-bk-text">{member.fullName}</span>
                        <span className="truncate text-[13px] text-bk-muted">
                          {member.email} · {member.phoneNumber}
                        </span>
                        <span className="font-mono text-[11.5px] text-bk-muted">{member.staffId}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-[18px] py-3.5">
                    <RolePill role={member.staffRole} />
                  </td>
                  <td className="px-[18px] py-3.5 text-bk-text">{member.branchProvinceName ?? '—'}</td>
                  <td className="px-[18px] py-3.5">
                    <StatusPill status={member.employmentStatus} />
                  </td>
                  <td className="px-[18px] py-3.5 text-right">
                    <button
                      type="button"
                      disabled={loadingStaffId === member.staffId}
                      onClick={() => openEditDialog(member)}
                      className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-3.5 py-2 text-[13px] font-bold text-bk-text hover:border-bk-teal hover:bg-bk-teal-soft disabled:opacity-50"
                    >
                      {loadingStaffId === member.staffId ? 'Loading…' : 'Edit'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {filtered.length === 0 && (
            <p className="px-[18px] py-10 text-center text-bk-muted">
              {staff.length === 0 ? 'No staff accounts yet.' : 'No staff match your search.'}
            </p>
          )}
        </div>
      )}

      <StaffFormDialog
        open={dialogMode != null}
        mode={dialogMode}
        initialValues={dialogInitialValues}
        pending={dialogPending}
        error={dialogError}
        onSubmit={handleSubmit}
        onCancel={closeDialog}
      />
    </AdminShell>
  )
}
