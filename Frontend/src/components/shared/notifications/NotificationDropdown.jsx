import { MaterialIcon } from '../icons'
import { NotificationItem } from './NotificationItem'

// The panel itself. Presentation only: every callback comes from NotificationBell,
// which owns the hook. This component never calls the service layer.

/** The states that replace the list entirely, rather than sitting above it. */
function PanelMessage({ icon, children }) {
  return (
    <div className="flex flex-col items-center gap-1.5 px-3.5 py-7 text-center text-[13px] text-bk-muted">
      <MaterialIcon name={icon} className="text-[26px] opacity-60" />
      {children}
    </div>
  )
}

/**
 * @param {object} props
 * @param {string} props.id matches the button's aria-controls
 * @param {string} props.labelledBy id of the heading that names this region
 * @param {Array<object>} props.items newest first, as the backend ordered them
 * @param {'loading'|'ready'|'error'} props.status
 * @param {boolean} props.isEmpty from the hook. Not `items.length === 0` — that
 *   flashes during the first load and sits on top of a failed refresh
 * @param {number} props.unreadCount the caller's total unread, not this page's
 * @param {number} props.now epoch milliseconds, captured when the panel opened
 */
export function NotificationDropdown({
  id,
  labelledBy,
  items,
  status,
  isEmpty,
  unreadCount,
  now,
  onActivate,
  onMarkAllRead,
  onRetry,
}) {
  const hasItems = items.length > 0

  return (
    <div
      id={id}
      role="region"
      aria-labelledby={labelledBy}
      className="absolute right-0 top-[46px] z-50 w-[min(340px,80vw)] animate-fx-in overflow-hidden
        rounded-[13px] border border-bk-border bg-bk-surface shadow-lg"
    >
      <div className="flex items-center justify-between gap-3 border-b border-bk-border px-3.5 py-3">
        <h2 id={labelledBy} className="m-0 text-[13px] font-bold text-bk-text">
          Notifications
        </h2>

        {/* Disabled at zero rather than hidden. The hook resolves true without a
            request when there is nothing to mark, so the press would be harmless —
            but a control that looks pressable and visibly does nothing is worse
            feedback than one that says up front there is nothing to do. Safe to
            gate on unreadCount because Section 9.1 reports the caller's total,
            not this page's. */}
        <button
          type="button"
          onClick={onMarkAllRead}
          disabled={unreadCount === 0}
          className="appearance-none text-[12px] font-semibold text-bk-teal hover:text-bk-teal-strong
            disabled:cursor-default disabled:text-bk-muted disabled:opacity-60"
        >
          Mark all read
        </button>
      </div>

      <div className="max-h-[320px] overflow-y-auto">
        {status === 'loading' && !hasItems && (
          <PanelMessage icon="hourglass_empty">Loading your notifications…</PanelMessage>
        )}

        {status === 'error' && !hasItems && (
          <PanelMessage icon="cloud_off">
            <span>We could not load your notifications.</span>
            <button
              type="button"
              onClick={onRetry}
              className="mt-1 appearance-none rounded-[9px] border border-bk-border px-3 py-1.5
                text-[12.5px] font-bold text-bk-text hover:bg-bk-bg"
            >
              Try again
            </button>
          </PanelMessage>
        )}

        {/* isEmpty is the hook's, and it is already gated on status === 'ready'.
            An ADMIN reaches this: the backend answers them with an empty page and
            unreadCount 0 rather than a 403, so no role branch is needed here —
            but the wording has to read as "nothing to show", never as a fault. */}
        {isEmpty && <PanelMessage icon="notifications_off">You&rsquo;re all caught up.</PanelMessage>}

        {hasItems && (
          <>
            {/* A refresh failed while a list was already on screen. The list stays —
                blanking something the reader is part-way through is a worse answer
                than telling them it may be stale. */}
            {status === 'error' && (
              <p
                role="status"
                className="m-0 flex items-center gap-2 border-b border-bk-border bg-bk-red-soft
                  px-3.5 py-2 text-[12px] text-bk-red"
              >
                <MaterialIcon name="cloud_off" className="text-base" />
                <span className="flex-1">Could not refresh.</span>
                <button
                  type="button"
                  onClick={onRetry}
                  className="appearance-none font-bold underline"
                >
                  Retry
                </button>
              </p>
            )}

            <ul className="m-0 list-none p-0">
              {items.map((notification) => (
                <li key={notification.notificationId}>
                  <NotificationItem
                    notification={notification}
                    now={now}
                    onActivate={onActivate}
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  )
}
