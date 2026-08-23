import { MaterialIcon } from '../shared/icons'
import { formatDurationMinutes, formatVnd } from '../../utils/format'

// Mirrors the five categories MockSmpBookingClient seeds today (Backend
// common/smp/client/mock/MockSmpBookingClient.java) — falls back to a generic
// icon for whatever a live SMP catalogue adds later.
const CATEGORY_ICONS = {
  Plumbing: 'plumbing',
  'Air Conditioning': 'ac_unit',
  Electrical: 'electrical_services',
  'Electronics Repair': 'devices',
  'Home Appliance': 'kitchen',
}
const DEFAULT_CATEGORY_ICON = 'handyman'

export function ServiceCard({ service, onSelect }) {
  const icon = CATEGORY_ICONS[service.categoryName] ?? DEFAULT_CATEGORY_ICON

  return (
    <button
      type="button"
      onClick={() => onSelect(service)}
      className="flex flex-col items-start gap-3 rounded-xl border border-bk-border bg-bk-surface p-[18px] text-left transition hover:border-bk-teal hover:shadow-sm"
    >
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[10px] bg-bk-teal-soft text-bk-teal-strong">
        <MaterialIcon name={icon} className="text-2xl" />
      </span>

      <span className="flex min-w-0 flex-col gap-1">
        <span className="text-[15.5px] font-bold text-bk-text">{service.serviceName}</span>
        <span className="text-[11.5px] font-bold uppercase tracking-[0.05em] text-bk-muted">
          {service.categoryName}
        </span>
      </span>

      {/* SMP's catalogue carries no description field today (SmpRepairServiceDto), so this
          is blank until a future release writes one — omitted rather than shown empty. */}
      {service.description && (
        <p className="line-clamp-2 text-[13px] text-bk-muted">{service.description}</p>
      )}

      <span className="mt-auto flex w-full items-center justify-between pt-1.5">
        <span className="text-[15px] font-extrabold text-bk-teal-strong">
          {formatVnd(service.baseServiceFee)}
        </span>
        <span className="flex items-center gap-1 text-[12.5px] text-bk-muted">
          <MaterialIcon name="schedule" className="text-base" />
          {formatDurationMinutes(service.standardDurationMinutes)}
        </span>
      </span>
    </button>
  )
}
