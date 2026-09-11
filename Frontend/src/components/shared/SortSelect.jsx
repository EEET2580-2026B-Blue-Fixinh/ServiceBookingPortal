import { MaterialIcon } from './icons'

/** A native <select> styled to match the admin toolbar's search input. */
export function SortSelect({ value, onChange, options, label = 'Sort by' }) {
  return (
    <div className="relative shrink-0">
      <select
        aria-label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-full appearance-none rounded-[9px] border border-bk-border bg-bk-surface py-2.5 pl-3.5 pr-9 font-[inherit] text-bk-text focus:outline-none focus:border-bk-teal focus:ring-[3px] focus:ring-bk-teal-soft"
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
      <MaterialIcon
        name="expand_more"
        className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-[18px] text-bk-muted"
      />
    </div>
  )
}
