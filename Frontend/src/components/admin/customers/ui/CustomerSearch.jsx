import { MaterialIcon } from '../../../shared/icons'
import { SortSelect } from '../../../shared/SortSelect'

/** The customer table's toolbar: search, sort, and match count. Lives inside CustomerList's card. */
export function CustomerSearch({ value, onChange, sortBy, sortOptions, onSortChange, matchCount, totalCount }) {
  return (
    <div className="flex items-center gap-3.5 border-b border-bk-border p-3.5 max-[700px]:flex-wrap">
      <div className="flex flex-1 items-center gap-2 rounded-[9px] border border-bk-border bg-bk-bg px-3 focus-within:border-bk-teal focus-within:ring-[3px] focus-within:ring-bk-teal-soft">
        <MaterialIcon name="search" className="shrink-0 text-[19px] text-bk-muted" />
        <input
          type="text"
          placeholder="Search by name, customer ID or email"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="min-w-0 flex-1 bg-transparent py-2.5 font-[inherit] text-bk-text focus:outline-none"
        />
      </div>
      <SortSelect value={sortBy} onChange={onSortChange} options={sortOptions} />
      <p className="shrink-0 font-mono text-[12.5px] text-bk-muted">
        {matchCount} of {totalCount} customers
      </p>
    </div>
  )
}
