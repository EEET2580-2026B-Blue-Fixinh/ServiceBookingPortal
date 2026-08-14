import { MaterialIcon } from '../shared/icons'

const STATUS_META = {
  scheduled: {
    label: 'Scheduled',
    icon: 'event_available',
    className: 'bg-bk-teal-soft text-bk-teal-strong',
  },
  confirmed: {
    label: 'Confirmed',
    icon: 'verified',
    className: 'bg-bk-blue-soft text-bk-blue',
  },
  working: {
    label: 'Working',
    icon: 'build',
    className: 'bg-bk-amber-soft text-bk-amber',
  },
  completed: {
    label: 'Completed',
    icon: 'check_circle',
    className: 'bg-bk-green-soft text-bk-green',
  },
  cancelled: {
    label: 'Cancelled',
    icon: 'cancel',
    className: 'bg-bk-red-soft text-bk-red',
  },
}

export function StatusBadge({ status }) {
  const meta = STATUS_META[status]
  if (!meta) return null

  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1.5 text-[12.5px] font-bold ${meta.className}`}
    >
      <MaterialIcon name={meta.icon} filled className="text-base" />
      {meta.label}
    </span>
  )
}
