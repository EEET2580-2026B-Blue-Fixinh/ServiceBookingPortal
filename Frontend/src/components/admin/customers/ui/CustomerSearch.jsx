import { ListToolbar } from '../../../shared/ListToolbar'

/** The customer table's toolbar: search, sort, and match count. Lives inside CustomerList's card. */
export function CustomerSearch({ value, onChange, sortBy, sortOptions, onSortChange, matchCount, totalCount }) {
  return (
    <ListToolbar
      value={value}
      onChange={onChange}
      placeholder="Search by name, customer ID or email"
      ariaLabel="Search customers by name, customer ID or email"
      sortBy={sortBy}
      sortOptions={sortOptions}
      onSortChange={onSortChange}
      matchCount={matchCount}
      totalCount={totalCount}
      itemNoun="customers"
    />
  )
}
