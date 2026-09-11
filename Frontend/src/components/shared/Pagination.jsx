import { MaterialIcon } from './icons'

// A compact run of page numbers: always the first and last page, the current
// page with one neighbour on each side, and a gap marker where the run breaks.
function pageWindow(current, pageCount) {
  const wanted = new Set([1, pageCount, current, current - 1, current + 1])
  const sorted = [...wanted].filter((page) => page >= 1 && page <= pageCount).sort((a, b) => a - b)

  const entries = []
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) entries.push('gap')
    entries.push(page)
  })
  return entries
}

const NAV_BUTTON =
  'flex h-8 w-8 items-center justify-center rounded-[8px] border border-bk-border bg-bk-surface ' +
  'text-bk-text hover:border-bk-teal hover:bg-bk-teal-soft disabled:opacity-40 ' +
  'disabled:hover:border-bk-border disabled:hover:bg-bk-surface'


export function Pagination({
  page,
  pageSize,
  totalItems,
  itemNoun,
  onPageChange,
  pageSizeOptions,
  onPageSizeChange,
}) {
  const pageCount = Math.max(1, Math.ceil(totalItems / pageSize))
  const current = Math.min(page, pageCount)
  const firstRow = totalItems === 0 ? 0 : (current - 1) * pageSize + 1
  const lastRow = Math.min(current * pageSize, totalItems)

  const go = (target) => onPageChange(Math.min(Math.max(target, 1), pageCount))

  return (
    <nav
      aria-label="Pagination"
      className="flex flex-wrap items-center justify-between gap-3 border-t border-bk-border px-[18px] py-3 text-[13px] text-bk-muted"
    >
      {pageSizeOptions && onPageSizeChange ? (
        <label className="flex items-center gap-1.5">
          Rows per page:
          <select
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
            className="rounded-[7px] border border-bk-border bg-bk-surface px-1.5 py-1 text-[13px] text-bk-text focus:border-bk-teal focus:outline-none"
          >
            {pageSizeOptions.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <p>Rows per page: {pageSize}</p>
      )}

      <p className="font-mono text-[12.5px]">
        {totalItems === 0
          ? `No ${itemNoun}`
          : `Showing ${firstRow} to ${lastRow} of ${totalItems} ${itemNoun}`}
      </p>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          className={NAV_BUTTON}
          onClick={() => go(1)}
          disabled={current === 1}
          aria-label="First page"
        >
          <MaterialIcon name="first_page" className="text-[18px]" />
        </button>
        <button
          type="button"
          className={NAV_BUTTON}
          onClick={() => go(current - 1)}
          disabled={current === 1}
          aria-label="Previous page"
        >
          <MaterialIcon name="chevron_left" className="text-[18px]" />
        </button>

        {pageWindow(current, pageCount).map((entry, index) =>
          entry === 'gap' ? (
            <span key={`gap-${index}`} className="px-1">
              …
            </span>
          ) : (
            <button
              key={entry}
              type="button"
              onClick={() => go(entry)}
              aria-current={entry === current ? 'page' : undefined}
              className={`flex h-8 min-w-8 items-center justify-center rounded-[8px] border px-2 text-[13px] font-bold ${
                entry === current
                  ? 'border-bk-teal bg-bk-teal text-white'
                  : 'border-bk-border bg-bk-surface text-bk-text hover:border-bk-teal hover:bg-bk-teal-soft'
              }`}
            >
              {entry}
            </button>
          ),
        )}

        <button
          type="button"
          className={NAV_BUTTON}
          onClick={() => go(current + 1)}
          disabled={current === pageCount}
          aria-label="Next page"
        >
          <MaterialIcon name="chevron_right" className="text-[18px]" />
        </button>
        <button
          type="button"
          className={NAV_BUTTON}
          onClick={() => go(pageCount)}
          disabled={current === pageCount}
          aria-label="Last page"
        >
          <MaterialIcon name="last_page" className="text-[18px]" />
        </button>
      </div>
    </nav>
  )
}
