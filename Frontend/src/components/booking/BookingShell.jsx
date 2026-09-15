import { AppShell } from '../shared/layout/AppShell'
import { NotificationBell } from '../shared/notifications/NotificationBell'

// The customer portal's frame. Layout and collapsing behaviour live in AppShell; this
// file owns only what is specific to customers: their navigation and the notification
// bell (notifications are addressed to customers, so the admin frame has none).
const NAV_ITEMS = [
  { key: 'bookings', label: 'My bookings', shortLabel: 'Bookings', icon: 'list_alt', to: '/booking' },
  { key: 'new', label: 'New booking', shortLabel: 'New', icon: 'add_circle', to: '/booking/new' },
  { key: 'ratings', label: 'My ratings', shortLabel: 'Ratings', icon: 'reviews', to: '/ratings' },
  { key: 'profile', label: 'Profile', shortLabel: 'Profile', icon: 'account_circle', to: '/profile' },
]

export function BookingShell({ activeNav, breadcrumb, title, headerActions, children }) {
  return (
    <AppShell
      portalTag="SBP"
      navItems={NAV_ITEMS}
      activeNav={activeNav}
      breadcrumb={breadcrumb}
      title={title}
      tools={<NotificationBell />}
      headerActions={headerActions}
    >
      {children}
    </AppShell>
  )
}
