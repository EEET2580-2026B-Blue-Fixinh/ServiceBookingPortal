function getInitials(fullName) {
  if (!fullName) return '?'
  const [first, ...rest] = fullName.trim().split(/\s+/)
  const last = rest.at(-1)
  return ((first?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase()
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

/** One row in the customer table (Req 3.5.S2). */
export function CustomerCard({ customer, cityName, pending, onRequestToggle }) {
  const isActive = customer.accountStatus === 'ACTIVE'

  return (
    <tr className="border-b border-bk-border last:border-0">
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
      <td className="px-[18px] py-3.5 text-bk-text">{cityName ?? '—'}</td>
      <td className="px-[18px] py-3.5">
        <StatusPill status={customer.accountStatus} />
      </td>
      <td className="px-[18px] py-3.5 text-right">
        <button
          type="button"
          disabled={pending}
          onClick={() => onRequestToggle(customer)}
          className={`appearance-none rounded-[9px] border px-3.5 py-2 text-[13px] font-bold disabled:opacity-50 ${
            isActive
              ? 'border-bk-border bg-bk-surface text-bk-red hover:border-bk-red hover:bg-bk-red-soft'
              : 'border-bk-teal bg-bk-teal text-white hover:bg-bk-teal-strong'
          }`}
        >
          {isActive ? 'Deactivate' : 'Activate'}
        </button>
      </td>
    </tr>
  )
}
