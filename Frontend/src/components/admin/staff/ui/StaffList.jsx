import { Pagination } from '../../../shared/Pagination'
import { StatCard } from '../../../shared/StatCard'
import { StaffCard } from './StaffCard'
import { StaffSearch } from './StaffSearch'

/** The stat cards plus the staff table and its search/sort toolbar (Req 3.5.S1). */
export function StaffList({
  staff,
  total,
  matchCount,
  loading,
  technicianCount,
  receptionistCount,
  loadingStaffId,
  onEdit,
  page,
  pageSize,
  onPageChange,
  pageSizeOptions,
  onPageSizeChange,
  search,
  onSearchChange,
  searching,
  sortBy,
  sortOptions,
  onSortChange,
  error,
  onRetry,
}) {
  return (
    <>
      <div className="mb-[18px] flex gap-3.5 max-[700px]:flex-col">
        <StatCard label="Technicians" value={technicianCount} />
        <StatCard label="Receptionists" value={receptionistCount} />
      </div>

      <div className="overflow-hidden rounded-xl border border-bk-border bg-bk-surface">
        <StaffSearch
          value={search}
          onChange={onSearchChange}
          matchCount={matchCount}
          totalCount={total}
          searching={searching}
          sortBy={sortBy}
          sortOptions={sortOptions}
          onSortChange={onSortChange}
        />

        {loading ? (
          <p className="px-[18px] py-10 text-center text-bk-muted">Loading staff…</p>
        ) : error && total === 0 ? (
          <div className="flex flex-col items-center gap-3 px-[18px] py-10 text-center">
            <p className="text-bk-red">Couldn't load staff accounts.</p>
            <button
              type="button"
              onClick={onRetry}
              className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 text-[13px] font-bold text-bk-text hover:border-bk-teal hover:bg-bk-teal-soft"
            >
              Try again
            </button>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] table-fixed border-collapse text-left text-[14px]">
                <colgroup>
                  <col className="w-[42%]" />
                  <col className="w-[16%]" />
                  <col className="w-[22%]" />
                  <col className="w-[10%]" />
                  <col className="w-[10%]" />
                </colgroup>
                <thead>
                  <tr className="border-b border-bk-border bg-bk-bg text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">
                    <th className="px-[18px] py-3">Staff member</th>
                    <th className="px-[18px] py-3">Role</th>
                    <th className="px-[18px] py-3">Branch</th>
                    <th className="px-[18px] py-3">Status</th>
                    <th className="px-[18px] py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {staff.map((member) => (
                    <StaffCard
                      key={member.staffId}
                      member={member}
                      loading={loadingStaffId === member.staffId}
                      onEdit={onEdit}
                    />
                  ))}
                </tbody>
              </table>
            </div>

            {matchCount === 0 ? (
              <p className="px-[18px] py-10 text-center text-bk-muted">
                {total === 0 ? 'No staff accounts yet.' : 'No staff match your search.'}
              </p>
            ) : (
              <Pagination
                page={page}
                pageSize={pageSize}
                totalItems={matchCount}
                itemNoun="staff accounts"
                onPageChange={onPageChange}
                pageSizeOptions={pageSizeOptions}
                onPageSizeChange={onPageSizeChange}
              />
            )}
          </>
        )}
      </div>
    </>
  )
}
