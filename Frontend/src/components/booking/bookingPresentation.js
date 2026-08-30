// Turns one row of GET /bookings (API Documentation Section 7.2) into the strings the
// booking list renders. Kept out of the page component so the page is layout and state,
// and every formatting decision has one home -- the same split notificationPresentation.js
// makes for the bell.

/**
 * Icons by catalogue category rather than by service name: Section 5.2 returns both, and
 * the category is the stable half. "Air Conditioning Service" could be renamed tomorrow;
 * the category it belongs to is what the icon is really about.
 */
const ICON_BY_CATEGORY = {
  Plumbing: 'plumbing',
  'Air Conditioning': 'ac_unit',
  Electrical: 'electrical_services',
  'Electronics Repair': 'devices',
  'Home Appliance': 'kitchen',
}

/** Every service the catalogue has ever had is not knowable, so unknown gets a real icon. */
const FALLBACK_ICON = 'handyman'

const DATE_FORMAT = { day: '2-digit', month: 'short', year: 'numeric' }
const TIME_FORMAT = { hour: '2-digit', minute: '2-digit', hour12: false }

// Section 2.6: the API speaks UTC and the frontend converts for display.
const DISPLAY_TIME_ZONE = 'Asia/Ho_Chi_Minh'

/**
 * @param {Array<{serviceId: string, categoryName: string}>} services the catalogue
 * @returns {(serviceId: string) => string} the material icon name for a booking's service
 */
export function iconResolver(services) {
  const categoryByServiceId = new Map(
    services.map((service) => [service.serviceId, service.categoryName]),
  )
  return (serviceId) =>
    ICON_BY_CATEGORY[categoryByServiceId.get(serviceId)] ?? FALLBACK_ICON
}

/** "2026-08-24" or an ISO instant, both as "24 Aug 2026" in local time. */
export function formatDate(value) {
  if (!value) return null
  // A date with no time is a calendar date, not midnight UTC -- converting it would slide
  // it a day backwards for anyone west of the line. Only instants get the time zone.
  const isPlainDate = /^\d{4}-\d{2}-\d{2}$/.test(value)
  const date = new Date(isPlainDate ? `${value}T00:00:00` : value)
  if (Number.isNaN(date.getTime())) return null

  return date.toLocaleDateString('en-GB', {
    ...DATE_FORMAT,
    ...(isPlainDate ? {} : { timeZone: DISPLAY_TIME_ZONE }),
  })
}

/** An ISO instant as "24 Aug 2026 · 18:00". */
export function formatDateTime(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null

  const day = date.toLocaleDateString('en-GB', { ...DATE_FORMAT, timeZone: DISPLAY_TIME_ZONE })
  const time = date.toLocaleTimeString('en-GB', { ...TIME_FORMAT, timeZone: DISPLAY_TIME_ZONE })
  return `${day} · ${time}`
}

/** Section 2.6: money is an integer of dong. 650000 reads as "650.000 đ". */
export function formatMoney(amount) {
  if (amount == null) return null
  return `${new Intl.NumberFormat('vi-VN').format(amount)} đ`
}

/**
 * The grey line under a booking's name. Built from what the row actually carries, so a
 * booking with no schedule yet says less rather than saying "null".
 */
export function buildMeta(booking) {
  const parts = []
  const created = formatDate(booking.createdAt)
  const preferred = formatDate(booking.preferredServiceDate)

  if (created) parts.push(`Created ${created}`)
  if (preferred) parts.push(`Preferred ${preferred}`)
  if (booking.technicians?.length) {
    parts.push(booking.technicians.map((technician) => technician.fullName).join(', '))
  }
  return parts.join(' · ')
}

/**
 * The teal schedule panel, or null when there is nothing to schedule yet.
 *
 * Section 7.2: only a SCHEDULED booking carries scheduledStartAt and technicians, and the
 * fields are absent rather than null when it does not -- so this asks whether they arrived,
 * not what the status says.
 */
export function buildSchedule(booking) {
  if (!booking.scheduledStartAt) return null

  const start = formatDateTime(booking.scheduledStartAt)
  const duration = booking.estimatedDurationMinutes
  return {
    label: duration ? `${start} · ${duration} min` : start,
    technicians: (booking.technicians ?? []).map((technician) => technician.fullName),
  }
}
