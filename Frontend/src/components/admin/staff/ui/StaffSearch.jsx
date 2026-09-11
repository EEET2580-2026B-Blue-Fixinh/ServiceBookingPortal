import { ListToolbar } from '../../../shared/ListToolbar'

/** The staff table's toolbar: search, sort, and match count. Lives inside StaffList's card. */
export function StaffSearch({ value, onChange, sortBy, sortOptions, onSortChange, matchCount, totalCount, searching }) {
  return (
    <ListToolbar
      value={value}
      onChange={onChange}
      placeholder="Search by name, staff ID or email"
      ariaLabel="Search staff by name, staff ID or email"
      sortBy={sortBy}
      sortOptions={sortOptions}
      onSortChange={onSortChange}
      matchCount={matchCount}
      totalCount={totalCount}
      itemNoun="staff accounts"
      searching={searching}
    />
  )
}
