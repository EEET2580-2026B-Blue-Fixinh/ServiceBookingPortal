import { MaterialIcon } from '../icons'
import { formatNotificationTime, presentationFor } from './notificationPresentation'

// One row. A pure renderer: it is handed a notification and a clock reading, and
// it decides nothing — the type table, the timezone conversion and the route all
// live in notificationPresentation.js, and what activation *does* belongs to the
// bell that owns the hook.

/**
 * @param {object} props
 * @param {object} props.notification one item from the Section 9.1 response
 * @param {number} props.now epoch milliseconds, captured when the panel opened.
 *   Passed in rather than read here so this stays pure — see the note on
 *   {@link formatNotificationTime}.
 * @param {(notification: object) => void} props.onActivate
 */
export function NotificationItem({ notification, now, onActivate }) {
  const { title, icon, iconClass } = presentationFor(notification.type)
  const isUnread = !notification.readStatus
  const timestamp = formatNotificationTime(notification.createdAt, now)

  return (
    <button
      type="button"
      onClick={() => onActivate(notification)}
      className={`flex w-full appearance-none items-start gap-2.5 border-b border-bk-border
        px-3.5 py-3 text-left last:border-b-0 hover:bg-bk-bg focus-visible:bg-bk-bg
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset
        focus-visible:ring-bk-teal ${isUnread ? 'bg-bk-teal-soft' : 'bg-transparent'}`}
    >
      <MaterialIcon name={icon} className={`mt-px text-lg ${iconClass}`} />

      <span className="flex min-w-0 flex-1 flex-col">
        <span
          className={`text-[13px] text-bk-text ${isUnread ? 'font-bold' : 'font-semibold'}`}
        >
          {title}
        </span>
        <span className="mt-0.5 text-[12px] leading-[1.4] text-bk-muted">
          {notification.message}
        </span>
        {timestamp && (
          <span className="mt-1 font-mono text-[10px] text-bk-muted">{timestamp}</span>
        )}
      </span>

      {/* Unread is signalled three ways -- background, weight and this dot -- because
          background alone is a colour-only distinction, which fails for anyone who
          cannot see the difference between a tinted row and a white one. The dot is
          aria-hidden and the text beside it is what a screen reader announces. */}
      {isUnread && (
        <>
          <span
            aria-hidden="true"
            className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-bk-teal"
          />
          <span className="sr-only">Unread</span>
        </>
      )}
    </button>
  )
}
