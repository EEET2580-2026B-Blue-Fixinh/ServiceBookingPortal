import { AdminShell } from '../../components/admin/AdminShell'
import { StaffForm } from '../../components/admin/staff/ui/StaffForm'
import { StaffSearch } from '../../components/admin/staff/ui/StaffSearch'
import { StaffList } from '../../components/admin/staff/ui/StaffList'
import { MaterialIcon } from '../../components/shared/icons'
import { useStaff } from '../../components/admin/staff/hooks/useStaff'
import { useStaffForm } from '../../components/admin/staff/hooks/useStaffForm'

export function AdminStaffPage() {
  const staff = useStaff()
  const form = useStaffForm({
    onNotice: staff.setNotice,
    onReload: staff.reload,
  })

  return (
    <AdminShell
      activeNav="staff"
      breadcrumb="Admin Portal"
      title="Staff & technicians"
      headerActions={
        <button
          type="button"
          onClick={form.openCreate}
          className="flex h-[38px] appearance-none items-center gap-1.5 rounded-[9px] bg-bk-teal px-4 text-sm font-bold text-white hover:bg-bk-teal-strong"
        >
          <MaterialIcon name="person_add" className="text-[19px]" />
          New staff account
        </button>
      }
    >
      {staff.error && (
        <p className="mb-4 rounded-[9px] border border-bk-red bg-bk-red-soft px-4 py-2.5 text-bk-red" role="alert">
          {staff.error}
        </p>
      )}

      {staff.notice && (
        <p
          className={
            staff.notice.variant === 'success'
              ? 'mb-4 rounded-[9px] border border-bk-green bg-bk-green-soft px-4 py-2.5 text-bk-green'
              : 'mb-4 rounded-[9px] border border-bk-amber bg-bk-amber-soft px-4 py-2.5 text-bk-amber'
          }
          role={staff.notice.variant === 'success' ? 'status' : 'alert'}
          aria-live={staff.notice.variant === 'success' ? 'polite' : 'assertive'}
        >
          {staff.notice.message}
        </p>
      )}

      <StaffSearch
        value={staff.search}
        onChange={staff.setSearch}
        matchCount={staff.staff.length}
        totalCount={staff.total}
        searching={staff.searching}
      />

      <StaffList
        staff={staff.staff}
        total={staff.total}
        loading={staff.loading}
        technicianCount={staff.technicianCount}
        receptionistCount={staff.receptionistCount}
        loadingStaffId={form.loadingStaffId}
        onEdit={form.openEdit}
      />

      <StaffForm
        open={form.mode != null}
        mode={form.mode}
        initialValues={form.initialValues}
        pending={form.pending}
        error={form.error}
        fieldErrors={form.fieldErrors}
        onSubmit={form.submit}
        onCancel={form.close}
      />
    </AdminShell>
  )
}
