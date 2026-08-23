import { MaterialIcon } from '../../../shared/icons'

export function CustomerSearch({ value, onChange, matchCount, totalCount }) {
  return (
    <div className="mb-[18px] flex items-center gap-3.5 max-[700px]:flex-wrap">
      <div className="flex flex-1 items-center gap-2 rounded-[9px] border border-bk-border bg-bk-surface px-3 focus-within:border-bk-teal focus-within:ring-[3px] focus-within:ring-bk-teal-soft">
        <MaterialIcon name="search" className="shrink-0 text-[19px] text-bk-muted" />
        <input
          type="text"
          placeholder="Search by name, customer ID or email"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="min-w-0 flex-1 bg-transparent py-2.5 font-[inherit] text-bk-text focus:outline-none"
        />
      </div>
      <p className="shrink-0 font-mono text-[12.5px] text-bk-muted">
        {matchCount} of {totalCount} customers
      </p>
    </div>
  )
}
