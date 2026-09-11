import { useState } from 'react'

export const PAGE_SIZE_OPTIONS = [5, 10, 20]

export function usePagedList(items, initialPageSize = PAGE_SIZE_OPTIONS[0]) {
  const [page, setPage] = useState(1)
  const [pageSize, setPageSizeState] = useState(initialPageSize)

  const pageCount = Math.max(1, Math.ceil(items.length / pageSize))
  const currentPage = Math.min(page, pageCount)
  const pageItems = items.slice((currentPage - 1) * pageSize, currentPage * pageSize)

  const resetPage = () => setPage(1)

  const setPageSize = (size) => {
    setPageSizeState(size)
    setPage(1)
  }

  return {
    page: currentPage,
    setPage,
    pageSize,
    setPageSize,
    pageSizeOptions: PAGE_SIZE_OPTIONS,
    pageItems,
    resetPage,
  }
}
