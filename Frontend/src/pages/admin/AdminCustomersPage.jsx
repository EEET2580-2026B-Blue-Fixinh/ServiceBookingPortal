import { useState } from 'react'
import { AdminShell } from '../../components/admin/AdminShell'
import { CustomerList } from '../../components/admin/customers/ui/CustomerList'
import { ActivateConfirmModal } from '../../components/admin/customers/ui/ActivateConfirmModal'
import { Toast } from '../../components/shared/Toast'
import { useCustomers } from '../../components/admin/customers/hooks/useCustomers'
import { useActivateCustomer } from '../../components/admin/customers/hooks/useActivateCustomer'

export function AdminCustomersPage() {
  const customers = useCustomers()
  const [toastMessage, setToastMessage] = useState(null)
  const activate = useActivateCustomer((updated) => {
    customers.replaceCustomer(updated)
    setToastMessage(
      updated.accountStatus === 'ACTIVE' ? `${updated.fullName} activated.` : `${updated.fullName} deactivated.`,
    )
  })

  const bannerMessage = activate.error || customers.error

  return (
    <AdminShell activeNav="customers" breadcrumb="Admin Portal" title="Customer accounts">
      {toastMessage && <Toast message={toastMessage} onDismiss={() => setToastMessage(null)} />}

      {bannerMessage && (
        <p className="mb-4 rounded-[9px] border border-bk-red bg-bk-red-soft px-4 py-2.5 text-bk-red" role="alert">
          {bannerMessage}
        </p>
      )}

      <CustomerList
        customers={customers.customers}
        total={customers.total}
        matchCount={customers.matchCount}
        loading={customers.loading}
        provinceNames={customers.provinceNames}
        activeCount={customers.activeCount}
        deactivatedCount={customers.deactivatedCount}
        pendingId={activate.pendingId}
        onRequestToggle={activate.requestToggle}
        page={customers.page}
        pageSize={customers.pageSize}
        onPageChange={customers.setPage}
        pageSizeOptions={customers.pageSizeOptions}
        onPageSizeChange={customers.setPageSize}
        search={customers.search}
        onSearchChange={customers.setSearch}
        sortBy={customers.sortBy}
        sortOptions={customers.sortOptions}
        onSortChange={customers.setSortBy}
        error={customers.error}
        onRetry={customers.reload}
      />

      <ActivateConfirmModal
        target={activate.confirmTarget}
        pending={activate.pendingId === activate.confirmTarget?.customerId}
        onConfirm={activate.confirmToggle}
        onCancel={activate.cancel}
      />
    </AdminShell>
  )
}
