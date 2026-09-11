import { MaterialIcon } from './icons'
import { SortSelect } from './SortSelect'

/** Search box, sort dropdown and match count, in the toolbar row of an admin list card. */
export function ListToolbar({
  value,
  onChange,
  placeholder,
  ariaLabel,
  sortBy,
  sortOptions,
  onSortChange,
  matchCount,
  totalCount,
  itemNoun,
  searching,
}) {
  return (
    <div className="flex items-center gap-3.5 border-b border-bk-border p-3.5 max-[700px]:flex-wrap">
      <div className="flex flex-1 items-center gap-2 rounded-[9px] border border-bk-border bg-bk-bg px-3 focus-within:border-bk-teal focus-within:ring-[3px] focus-within:ring-bk-teal-soft">
        <MaterialIcon name="search" className="shrink-0 text-[19px] text-bk-muted" />
        <input
          type="search"
          aria-label={ariaLabel}
          placeholder={placeholder}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="min-w-0 flex-1 bg-transparent py-2.5 font-[inherit] text-bk-text focus:outline-none"
        />
      </div>
      <SortSelect value={sortBy} onChange={onSortChange} options={sortOptions} />
      <p className="shrink-0 font-mono text-[12.5px] text-bk-muted">
        {searching ? 'Searching…' : `${matchCount} of ${totalCount} ${itemNoun}`}
      </p>
    </div>
  )
}
