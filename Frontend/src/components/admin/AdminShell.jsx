import { AppShell } from '../shared/layout/AppShell'

// The admin portal's frame. Layout and collapsing behaviour live in AppShell; this file
// owns only the admin navigation.
const NAV_ITEMS = [
  { key: 'dashboard', label: 'Dashboard', shortLabel: 'Dashboard', icon: 'dashboard', to: '/admin' },
  { key: 'customers', label: 'Customers', shortLabel: 'Customers', icon: 'group', to: '/admin/customers' },
  { key: 'staff', label: 'Staff & technicians', shortLabel: 'Staff', icon: 'engineering', to: '/admin/staff' },
]

export function AdminShell({ activeNav, breadcrumb, title, headerActions, children }) {
  return (
    <AppShell
      portalTag="Admin"
      navItems={NAV_ITEMS}
      activeNav={activeNav}
      breadcrumb={breadcrumb}
      title={title}
      headerActions={headerActions}
    >
      {children}
    </AppShell>
  )
}
