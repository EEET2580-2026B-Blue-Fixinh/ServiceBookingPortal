import { MaterialIcon } from '../shared/icons'

// Keyed by the API's own values (Appendix A), not by a lower-cased dialect of them. All
// eight are here because all eight can arrive: a badge that silently renders nothing is
// worse than a plain one, and that is exactly what happened while CREATED was missing --
// every real booking is CREATED until SMP moves it, so the status column was simply blank.
const STATUS_META = {
  CREATED: {
    label: 'Created',
    icon: 'schedule',
    className: 'bg-bk-bg text-bk-muted',
  },
  CONFIRMED: {
    label: 'Confirmed',
    icon: 'verified',
    className: 'bg-bk-blue-soft text-bk-blue',
  },
  SCHEDULED: {
    label: 'Scheduled',
    icon: 'event_available',
    className: 'bg-bk-teal-soft text-bk-teal-strong',
  },
  WORKING: {
    label: 'Working',
    icon: 'build',
    className: 'bg-bk-amber-soft text-bk-amber',
  },
  COMPLETED: {
    label: 'Completed',
    icon: 'check_circle',
    className: 'bg-bk-green-soft text-bk-green',
  },
  CANCELLED_BY_CUSTOMER: {
    label: 'Cancelled',
    icon: 'cancel',
    className: 'bg-bk-red-soft text-bk-red',
  },
  // Distinct from a cancellation, and labelled as such: a technician was already on site,
  // which is why Req 3.3.M1 gives it its own status rather than folding it into the one above.
  TERMINATED_BY_CUSTOMER: {
    label: 'Terminated',
    icon: 'block',
    className: 'bg-bk-red-soft text-bk-red',
  },
  ABORTED_BY_TECHNICIAN: {
    label: 'Aborted',
    icon: 'report',
    className: 'bg-bk-red-soft text-bk-red',
  },
}

export function StatusBadge({ status }) {
  const meta = STATUS_META[status]

  // Appendix A calls an unlisted value "a defect, not a feature -- clients should log and
  // ignore rather than guess". Showing the raw value is that: it names what arrived instead
  // of hiding the row's status entirely.
  if (!meta) {
    return (
      <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full bg-bk-bg px-3 py-1.5 text-[12.5px] font-bold text-bk-muted">
        {status ?? 'Unknown'}
      </span>
    )
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1.5 text-[12.5px] font-bold ${meta.className}`}
    >
      <MaterialIcon name={meta.icon} filled className="text-base" />
      {meta.label}
    </span>
  )
}
