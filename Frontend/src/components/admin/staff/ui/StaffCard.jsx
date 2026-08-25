function getInitials(fullName) {
  if (!fullName) return '?'
  const [first, ...rest] = fullName.trim().split(/\s+/)
  const last = rest.at(-1)
  return ((first?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase()
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

/** One row in the staff table (Req 3.5.S1). */
export function StaffCard({ member, loading, onEdit }) {
  return (
    <tr className="border-b border-bk-border last:border-0">
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
          disabled={loading}
          onClick={() => onEdit(member)}
          className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-3.5 py-2 text-[13px] font-bold text-bk-text hover:border-bk-teal hover:bg-bk-teal-soft disabled:opacity-50"
        >
          {loading ? 'Loading…' : 'Edit'}
        </button>
      </td>
    </tr>
  )
}
