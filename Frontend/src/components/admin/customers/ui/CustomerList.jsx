import { Pagination } from '../../../shared/Pagination'
import { CustomerCard } from './CustomerCard'
import { CustomerSearch } from './CustomerSearch'

function StatCard({ label, value }) {
  return (
    <div className="flex-1 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
      <p className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">{label}</p>
      <p className="text-[26px] font-extrabold text-bk-text">{value}</p>
    </div>
  )
}

/** The stat cards plus the customer table and its search/sort toolbar (Req 3.5.S2). */
export function CustomerList({
  customers,
  total,
  matchCount,
  loading,
  provinceNames,
  activeCount,
  deactivatedCount,
  pendingId,
  onRequestToggle,
  page,
  pageSize,
  onPageChange,
  search,
  onSearchChange,
  sortBy,
  sortOptions,
  onSortChange,
}) {
  return (
    <>
      <div className="mb-[18px] flex gap-3.5 max-[700px]:flex-col">
        <StatCard label="Active" value={activeCount} />
        <StatCard label="Deactivated" value={deactivatedCount} />
      </div>

      <div className="overflow-hidden rounded-xl border border-bk-border bg-bk-surface">
        <CustomerSearch
          value={search}
          onChange={onSearchChange}
          matchCount={matchCount}
          totalCount={total}
          sortBy={sortBy}
          sortOptions={sortOptions}
          onSortChange={onSortChange}
        />

        {loading ? (
          <p className="px-[18px] py-10 text-center text-bk-muted">Loading customers…</p>
        ) : (
          <>
            <table className="w-full table-fixed border-collapse text-left text-[14px]">
              <colgroup>
                <col className="w-[46%]" />
                <col className="w-[20%]" />
                <col className="w-[16%]" />
                <col className="w-[18%]" />
              </colgroup>
              <thead>
                <tr className="border-b border-bk-border bg-bk-bg text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">
                  <th className="px-[18px] py-3">Customer</th>
                  <th className="px-[18px] py-3">City</th>
                  <th className="px-[18px] py-3">Status</th>
                  <th className="px-[18px] py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {customers.map((customer) => (
                  <CustomerCard
                    key={customer.customerId}
                    customer={customer}
                    cityName={provinceNames[customer.address?.provinceCode] ?? customer.address?.provinceCode}
                    pending={pendingId === customer.customerId}
                    onRequestToggle={onRequestToggle}
                  />
                ))}
              </tbody>
            </table>

            {matchCount === 0 ? (
              <p className="px-[18px] py-10 text-center text-bk-muted">
                {total === 0 ? 'No customers yet.' : 'No customers match your search.'}
              </p>
            ) : (
              <Pagination
                page={page}
                pageSize={pageSize}
                totalItems={matchCount}
                itemNoun="customers"
                onPageChange={onPageChange}
              />
            )}
          </>
        )}
      </div>
    </>
  )
}
