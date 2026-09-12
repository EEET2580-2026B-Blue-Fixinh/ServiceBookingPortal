import { ROUTES } from '../../../config/constants'

/**
 * The icons follow the mockup, not StatusBadge. StatusBadge maps a *booking* to
 * `verified` for confirmed; a notification about that booking is a different
 * object in a different surface, and the mockup draws it as `task_alt`. Matching
 * the mockup here was the deliberate choice — flagged rather than silently
 * reconciled, because the two files now disagree on purpose.
 *
 * `tone` is for the toast a pushed notification raises, and is also the mockup's:
 * a confirmation is good news (`ok`), a schedule is the primary event (`pri`).
 * The toast's icon comes from the tone — `ok` draws `check_circle` — which is
 * why the panel's `task_alt` for the same type is not a contradiction: the mockup
 * gives the two surfaces different icons on purpose.
 */
const PRESENTATION_BY_TYPE = {
  BOOKING_CONFIRMED: {
    title: 'Booking confirmed',
    icon: 'task_alt',
    iconClass: 'text-bk-blue',
    tone: 'ok',
  },
  BOOKING_SCHEDULED: {
    title: 'Technician scheduled',
    icon: 'event_available',
    iconClass: 'text-bk-teal',
    tone: 'pri',
  },
  // Addressed to STAFF, who have no SBP session, so this should never reach a
  // bell.
  STAFF_ACCOUNT_CREATED: {
    title: 'Staff account created',
    icon: 'badge',
    iconClass: 'text-bk-muted',
    tone: 'neutral',
  },
}

/**
 * The fallback for a type this build has never heard of.
 *
 * A notification is worth showing even when its type is not: the message is the
 * part the recipient acts on, and Appendix A gaining a fourth value should
 * degrade the icon, not blank the row.
 */
const UNKNOWN_TYPE = {
  title: 'Notification',
  icon: 'notifications',
  iconClass: 'text-bk-muted',
  tone: 'neutral',
}

export function presentationFor(type) {
  return PRESENTATION_BY_TYPE[type] ?? UNKNOWN_TYPE
}

/**
 * The toast a notification raises when it arrives live over the real-time
 * channel (Section 9.3): the type's title and tone, and the message as the body.
 * Same table as the panel, so the two can never call one type by two names.
 */
export function toastFor(notification) {
  const { title, tone } = presentationFor(notification.type)
  // `to` rather than a click handler: the queue holds data, and what a tone or a
  // destination looks like is the stack's business. A notification about no
  // booking has nowhere to go and yields a toast with no `to` at all, which the
  // stack draws as a plain card.
  return { tone, title, body: notification.message, to: routeFor(notification) }
}

// The API sends ISO-8601 UTC and the frontend displays
// Asia/Ho_Chi_Minh. Built once at module load rather than per row — constructing
// an Intl formatter is the expensive part, and formatting with it is not.
const TIME_ZONE = 'Asia/Ho_Chi_Minh'

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIME_ZONE,
  day: 'numeric',
  month: 'short',
})

const TIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/** Below this, the mockup shows "Just now" rather than a clock time. */
const JUST_NOW_MS = 60_000

/**
 * A notification's `createdAt` as the mockup writes it: "Just now", or
 * "8 Aug, 16:52" in Asia/Ho_Chi_Minh.
 *
 * `now` is a parameter rather than a `Date.now()` call inside this function, and
 * that is load-bearing. This runs during render, and react-hooks/purity forbids
 * reading the clock there — a render that returns something different on a
 * re-render with identical props is exactly what the rule catches. NotificationBell
 * captures the clock when the panel opens and passes it down, so "Just now" means
 * "when you opened this", which is also the more honest reading.
 *
 * @param {string} createdAt ISO-8601 UTC, per Section 2.6
 * @param {number} now epoch milliseconds, captured outside render
 * @returns {string} empty when `createdAt` is missing or unparseable — a row with
 *   a broken timestamp still shows its message rather than "Invalid Date"
 */
export function formatNotificationTime(createdAt, now) {
  if (!createdAt) return ''

  const createdAtMs = Date.parse(createdAt)
  if (Number.isNaN(createdAtMs)) return ''

  if (now - createdAtMs < JUST_NOW_MS) return 'Just now'

  const created = new Date(createdAtMs)
  return `${DATE_FORMAT.format(created)}, ${TIME_FORMAT.format(created)}`
}

/**
 * Where opening a notification should take the reader, or null when there is
 * nowhere to go.
 *
 * <p>Null is a normal answer, not a failure: `bookingRef` is absent — not null —
 * on a notification that concerns no booking (`@JsonInclude(NON_NULL)` on the
 * DTO), and STAFF_ACCOUNT_CREATED never has one. The item renders both shapes.
 *
 * <p>The booking itself, not the bookings list. A notification names one booking
 * and the reader opened it to see that one; landing on the list would make them
 * find it again. Both surfaces that open a notification — the panel and the toast
 * a push raises — go through here, so the two cannot drift apart.
 */
export function routeFor(notification) {
  return notification.bookingRef ? ROUTES.bookingDetail(notification.bookingRef) : null
}