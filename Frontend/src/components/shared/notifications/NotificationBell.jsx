import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MaterialIcon } from '../icons'
import { useNotifications } from '../../../hooks/useNotifications'
import { NotificationDropdown } from './NotificationDropdown'
import { routeFor } from './notificationPresentation'

// The bell, and the only place in the app that calls useNotifications.

const PANEL_ID = 'notification-panel'
const PANEL_HEADING_ID = 'notification-panel-heading'

/** Past this the badge would widen the button, so it stops counting. */
const MAX_BADGE_COUNT = 99

export function NotificationBell() {
  const { items, unreadCount, status, isEmpty, refresh, retry, markRead, markAllRead } =
    useNotifications()

  const navigate = useNavigate()

  // One state value, not an `isOpen` boolean beside an `openedAt` number: they
  // are set and cleared together, and two useStates could be torn apart by a
  // future edit that updates one of them. Null is closed.
  const [panel, setPanel] = useState(null)
  const isOpen = panel !== null

  const buttonRef = useRef(null)
  const containerRef = useRef(null)

  const close = useCallback(({ restoreFocus } = {}) => {
    setPanel(null)
    // Escape returns focus to the bell, because the keyboard user asked to leave
    // the panel and has nowhere else to be. An outside click does not: they have
    // already chosen where to go, and yanking focus back would fight them.
    if (restoreFocus) buttonRef.current?.focus()
  }, [])

  const toggle = () => {
    if (isOpen) {
      close({ restoreFocus: true })
      return
    }

    // Date.now() is read here, in an event handler, and never during render --
    // react-hooks/purity forbids the latter. It is what "Just now" is measured
    // against, so the label means "when you opened this".
    setPanel({ openedAt: Date.now() })

    // The silent one: the list already on screen stays put while it runs, so
    // opening the panel does not flash a spinner over notifications the reader
    // could already see behind the badge count.
    refresh()
  }

  // Escape and outside-click dismissal. Registered only while open, so the app
  // is not listening on document for the whole session. The effect body only adds
  // listeners -- no setState here, which is what react-hooks/set-state-in-effect
  // is watching for; the setState happens inside the callbacks, on a real event.
  useEffect(() => {
    if (!isOpen) return undefined

    const onKeyDown = (event) => {
      if (event.key === 'Escape') close({ restoreFocus: true })
    }

    const onPointerDown = (event) => {
      if (!containerRef.current?.contains(event.target)) close()
    }

    document.addEventListener('keydown', onKeyDown)
    // mousedown, not click: a click fires after the press completes, so a press
    // that starts inside the panel and drifts outside while selecting text would
    // dismiss the panel out from under the selection.
    document.addEventListener('mousedown', onPointerDown)

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('mousedown', onPointerDown)
    }
  }, [isOpen, close])

  /**
   * Opening one notification: mark it read, close, and go where it points.
   *
   * Not awaited. markRead is optimistic and never rejects -- it reverts itself if
   * the backend refuses -- so waiting would hold the panel open on a request whose
   * result changes nothing about where we are going.
   */
  const handleActivate = (notification) => {
    markRead(notification.notificationId)
    close()

    const route = routeFor(notification)
    if (route) navigate(route)
  }

  const badgeCount = unreadCount > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : unreadCount

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={toggle}
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        aria-expanded={isOpen}
        aria-controls={PANEL_ID}
        // "dialog", not "true". aria-haspopup="true" means menu, and a menu
        // promises arrow-key navigation between its items; this panel is a list of
        // buttons reached by Tab. Claiming the semantics without the keyboard
        // behaviour is worse for a screen-reader user than not claiming them.
        aria-haspopup="dialog"
        className={`relative flex h-[38px] w-[38px] appearance-none items-center justify-center
          rounded-[9px] border bg-bk-surface text-bk-text hover:border-bk-muted
          ${isOpen ? 'border-bk-teal text-bk-teal' : 'border-bk-border'}`}
      >
        <MaterialIcon name="notifications" />
        {unreadCount > 0 && (
          <span
            aria-hidden="true"
            className="absolute -right-[5px] -top-[5px] flex h-4 min-w-[16px] items-center
              justify-center rounded-full bg-bk-red px-[3px] text-[10px] font-bold leading-none
              text-white"
          >
            {badgeCount}
          </span>
        )}
      </button>

      {isOpen && (
        <NotificationDropdown
          id={PANEL_ID}
          labelledBy={PANEL_HEADING_ID}
          items={items}
          status={status}
          isEmpty={isEmpty}
          unreadCount={unreadCount}
          now={panel.openedAt}
          onActivate={handleActivate}
          onMarkAllRead={markAllRead}
          onRetry={retry}
        />
      )}
    </div>
  )
}
