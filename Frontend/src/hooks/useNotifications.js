import { useCallback, useEffect, useRef, useState } from 'react'
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../services/api/endpoints/notificationEndpoints'
import { useAuth } from '../context/AuthContext'

// Owns the caller's own notification list, its unread count and the two ways of
// clearing it.
//
// The list and the count live in one state object rather than two, because they
// have to agree: marking one read has to move both, and two setState calls can be
// interleaved by a refresh landing between them, leaving a badge that contradicts
// the list under it. One object makes every update atomic by construction.

const EMPTY = Object.freeze({ items: [], unreadCount: 0, page: null })

/**
 * One notification marked read, or the same snapshot back when there is nothing
 * to do — the caller relies on that identity to skip a pointless re-render.
 */
function withRead(data, notificationId) {
  const target = data.items.find((item) => item.notificationId === notificationId)
  if (!target || target.readStatus) return data

  return {
    ...data,
    items: data.items.map((item) =>
      item.notificationId === notificationId ? { ...item, readStatus: true } : item,
    ),
    unreadCount: Math.max(0, data.unreadCount - 1),
  }
}

/**
 * Every notification marked read, or the same snapshot back when none was unread.
 *
 * The count goes to zero rather than down by the number of rows on this page:
 */
function withAllRead(data) {
  if (data.unreadCount === 0 && data.items.every((item) => item.readStatus)) return data

  return {
    ...data,
    items: data.items.map((item) => (item.readStatus ? item : { ...item, readStatus: true })),
    unreadCount: 0,
  }
}

export function useNotifications() {
  const { isAuthenticated } = useAuth()

  const [data, setData] = useState(EMPTY)
  const [status, setStatus] = useState('loading')

  // The same value as `data`, readable synchronously. An optimistic update has to
  // see the result of the one before it, and state read during render is a frame
  // behind — two clicks in the same tick would both compute from the same starting
  // point and the second would undo the first. Reading a functional updater's
  // argument instead would mean writing outside it, which is the impure-updater
  // pattern react-hooks warns about and StrictMode runs twice.
  const dataRef = useRef(EMPTY)

  // apiClient takes no AbortSignal, so a request in flight cannot be cancelled,
  // only its result ignored. Each load claims a number and drops its own response
  // if that number is no longer current — without it a slow refresh fired on open
  // can land after a quick one fired on return and reinstate what it replaced.
  const requestIdRef = useRef(0)

  const applyData = useCallback((next) => {
    if (next === dataRef.current) return
    dataRef.current = next
    setData(next)
  }, [])

  const load = useCallback(() => {
    // No session, nothing addressed to anyone. Bumping the id first discards any
    // response still in flight from the session that just ended.
    if (!isAuthenticated) {
      requestIdRef.current += 1
      return
    }

    requestIdRef.current += 1
    const requestId = requestIdRef.current

    // No `size` argument: Section 9.1's default belongs to the backend, and a copy
    // of it here would be one more thing to keep in step by hand.
    fetchNotifications()
      .then((body) => {
        if (requestId !== requestIdRef.current) return
        applyData({
          items: body.items ?? [],
          unreadCount: body.unreadCount ?? 0,
          page: body.page ?? null,
        })
        setStatus('ready')
      })
      .catch(() => {
        if (requestId !== requestIdRef.current) return
        // The previous list stays on screen. A refresh that fails is a reason to say
        // so, not a reason to blank a list someone is reading. An expired token needs
        // nothing here either: apiClient has already told AuthProvider, which ends
        // the session and routes to the login page.
        setStatus('error')
      })
  }, [isAuthenticated, applyData])

  useEffect(() => {
    load()
  }, [load])

  // Refreshed when the reader comes back to the tab, never on a timer: polling
  // keeps asking while nobody is looking, and the badge only has to be right at the
  // moment someone can see it. Live delivery is 5.1's job, not a shorter interval.
  //
  // visibilitychange rather than focus, matching AuthProvider — focus also fires on
  // every click back into a window that was already visible, which is not a return.
  useEffect(() => {
    const refreshOnReturn = () => {
      if (document.visibilityState === 'visible') load()
    }

    document.addEventListener('visibilitychange', refreshOnReturn)
    return () => document.removeEventListener('visibilitychange', refreshOnReturn)
  }, [load])

  /**
   * Marks one notification read, showing it immediately and reverting if the
   * request fails.
   *
   * @returns {Promise<boolean>} false only when the backend refused. Already-read
   *   and not-on-this-page both resolve true, having done nothing.
   */
  const markRead = useCallback(
    async (notificationId) => {
      const before = dataRef.current
      const optimistic = withRead(before, notificationId)
      if (optimistic === before) return true

      applyData(optimistic)

      try {
        await markNotificationRead(notificationId)
        // Applied again rather than assumed to have stuck: a refresh may have landed
        // in between carrying the row as it was before the PATCH, which would show
        // the dot again for something the backend has already marked.
        applyData(withRead(dataRef.current, notificationId))
        return true
      } catch {
        // Put it back, unless something newer has replaced what was changed — the
        // server's own answer is worth more than a snapshot taken before the call.
        // The dot reappearing is the feedback; deliberately no error banner, which
        // would be a lot of furniture for something reopening the item retries.
        if (dataRef.current === optimistic) applyData(before)
        return false
      }
    },
    [applyData],
  )

  /**
   * Marks everything read. Same optimistic-then-revert shape as {@link markRead}.
   *
   * @returns {Promise<boolean>} false only when the backend refused. Nothing unread
   *   resolves true without a request, so the control never needs to check first.
   */
  const markAllRead = useCallback(async () => {
    const before = dataRef.current
    const optimistic = withAllRead(before)
    if (optimistic === before) return true

    applyData(optimistic)

    try {
      await markAllNotificationsRead()
      applyData(withAllRead(dataRef.current))
      return true
    } catch {
      if (dataRef.current === optimistic) applyData(before)
      return false
    }
  }, [applyData])

  // Silent, for opening the panel or returning to the tab: the list on screen stays
  // put while it runs. `retry` is the visible one, for the error state's button.
  const retry = useCallback(() => {
    setStatus('loading')
    load()
  }, [load])

  const visible = isAuthenticated ? data : EMPTY
  const visibleStatus = isAuthenticated ? status : 'ready'

  return {
    items: visible.items,
    unreadCount: visible.unreadCount,
    /** Section 9.1's page block, or null before the first response lands. */
    page: visible.page,
    status: visibleStatus,
    // Derived here so the empty state cannot be shown while the first load is still
    // running, or on top of a list that failed to refresh.
    isEmpty: visibleStatus === 'ready' && visible.items.length === 0,
    refresh: load,
    retry,
    markRead,
    markAllRead,
  }
}