import { Link, useNavigate } from 'react-router-dom'
import { MaterialIcon } from '../icons'
import { useAuth } from '../../../context/AuthContext'
import { getInitials } from '../../../utils/format'

// The signed-in frame every portal screen sits in. One component rather than one per
// portal so the two portals can never drift apart in how they collapse: the customer
// and admin shells differ only in their navigation items, their tag and their header
// tools, and those come in as props.
//
// Two layouts, switched at Tailwind's `lg` breakpoint (64rem / 1024px):
//   - wide: a sticky sidebar carrying brand, navigation and the signed-in user; the
//     header carries breadcrumb, title and tools.
//   - narrow: no sidebar. The header carries the brand and the tools, the title moves
//     into the page body, and navigation becomes a bottom tab bar that stays in reach
//     of a thumb (design mockup, mobile frame). The user card goes with the sidebar —
//     signing out stays available on the Profile screen.
//
// The frame is not position:fixed. The document itself scrolls, which is what lets the
// sidebar be `sticky`, the header stay put, and mobile browsers collapse their address
// bar the way users expect; an inner scroll box would defeat all three.

/**
 * @param {object} props
 * @param {string} props.portalTag  short badge next to the brand, e.g. "SBP" or "Admin"
 * @param {Array<{key: string, label: string, shortLabel: string, icon: string, to: string}>} props.navItems
 * @param {string} props.activeNav  the `key` of the current item
 * @param {string} props.breadcrumb
 * @param {string} props.title
 * @param {import('react').ReactNode} [props.tools]  portal-level header tools (e.g. notifications)
 * @param {import('react').ReactNode} [props.headerActions]  page-level actions (e.g. "New booking")
 * @param {import('react').ReactNode} props.children
 */
export function AppShell({ portalTag, navItems, activeNav, breadcrumb, title, tools, headerActions, children }) {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const handleSignOut = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div
      className="grid min-h-svh bg-bk-bg text-left text-[15px] leading-[1.45] text-bk-text
        lg:grid-cols-[260px_minmax(0,1fr)]
        [--auth-border:var(--color-bk-border)] [--auth-muted:var(--color-bk-muted)]
        [--auth-teal:var(--color-bk-teal)] [--auth-text:var(--color-bk-text)]"
    >
      <aside className="sticky top-0 hidden h-svh flex-col overflow-y-auto border-r border-bk-border bg-bk-surface p-[22px_18px] lg:flex">
        <div className="flex items-center gap-2.5 px-2 pb-[22px]">
          <BrandMark tag={portalTag} />
        </div>

        <nav aria-label="Main" className="flex flex-col gap-0.5">
          {navItems.map((item) => (
            <SidebarNavItem key={item.key} item={item} isActive={item.key === activeNav} />
          ))}
        </nav>

        <div className="mt-auto flex flex-col gap-3 rounded-[10px] border border-bk-border p-3">
          <div className="flex items-center gap-2.5">
            <span className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-full bg-bk-teal-soft text-[13px] font-bold text-bk-teal-strong">
              {getInitials(user?.fullName)}
            </span>
            <span className="flex min-w-0 flex-col">
              <span className="truncate text-[13.5px] font-bold">{user?.fullName ?? 'Signed in'}</span>
              <span className="truncate text-xs text-bk-muted">{user?.email ?? ''}</span>
            </span>
          </div>
          <button
            type="button"
            onClick={handleSignOut}
            className="flex w-full appearance-none items-center justify-center gap-2 rounded-[9px] border
              border-bk-border px-3 py-[9px] text-[13.5px] font-bold text-bk-text hover:border-bk-red
              hover:bg-bk-red-soft hover:text-bk-red"
          >
            <MaterialIcon name="logout" className="text-lg" />
            Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-col">
        {/* One step above the tab bar so the notification dropdown, which hangs off this
            header, wins where the two would meet on a short landscape screen. Dialogs and
            toasts sit at z-[200] and clear both. */}
        <header className="sticky top-0 z-40 flex items-center gap-3 border-b border-bk-border bg-bk-surface px-4 py-3 lg:px-[clamp(20px,3vw,40px)] lg:py-[18px]">
          <div className="flex items-center gap-2.5 lg:hidden">
            <BrandMark />
          </div>
          <div className="hidden min-w-0 lg:block">
            <p className="mb-0.5 text-[12.5px] text-bk-muted">{breadcrumb}</p>
            <h1 className="m-0 truncate text-2xl font-extrabold tracking-[-0.3px] text-bk-text">{title}</h1>
          </div>

          <div className="ml-auto flex shrink-0 items-center gap-2.5">
            {tools}
            {headerActions}
          </div>
        </header>

        {/* Bottom padding on narrow screens clears the tab bar so the last card is never
            hidden behind it. */}
        <main className="flex-1 px-4 pb-6 pt-4 lg:px-[clamp(20px,3vw,40px)] lg:pb-10 lg:pt-[clamp(16px,3vw,28px)]">
          <div className="mx-auto w-full max-w-[1140px]">
            <h1 className="mb-3.5 text-[22px] font-extrabold tracking-[-0.3px] text-bk-text lg:hidden">{title}</h1>
            {children}
          </div>
        </main>

        <nav
          aria-label="Main"
          className="sticky bottom-0 z-30 flex border-t border-bk-border bg-bk-surface px-1.5 pt-1.5
            pb-[max(8px,env(safe-area-inset-bottom))] lg:hidden"
        >
          {navItems.map((item) => (
            <BottomNavItem key={item.key} item={item} isActive={item.key === activeNav} />
          ))}
        </nav>
      </div>
    </div>
  )
}

function BrandMark({ tag }) {
  return (
    <>
      <span className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[10px] bg-bk-teal text-white">
        <MaterialIcon name="handyman" />
      </span>
      <span className="text-lg font-extrabold tracking-[-0.2px]">Fixinh</span>
      {tag && (
        <span className="rounded-full border border-bk-border bg-bk-bg px-[7px] py-0.5 text-[10.5px] font-bold tracking-[0.04em] text-bk-muted">
          {tag}
        </span>
      )}
    </>
  )
}

function SidebarNavItem({ item, isActive }) {
  return (
    <Link
      to={item.to}
      aria-current={isActive ? 'page' : undefined}
      className={`flex w-full items-center gap-3 rounded-[9px] px-3 py-2.5 font-semibold no-underline ${
        isActive ? 'bg-bk-teal-soft text-bk-teal-strong' : 'text-bk-text hover:bg-bk-bg'
      }`}
    >
      <MaterialIcon name={item.icon} className={`text-xl ${isActive ? 'text-bk-teal' : 'text-bk-muted'}`} />
      {item.label}
    </Link>
  )
}

function BottomNavItem({ item, isActive }) {
  return (
    <Link
      to={item.to}
      aria-current={isActive ? 'page' : undefined}
      className={`flex min-h-[52px] flex-1 flex-col items-center justify-center gap-0.5 rounded-[10px] no-underline ${
        isActive ? 'bg-bk-teal-soft text-bk-teal-strong' : 'text-bk-muted'
      }`}
    >
      <MaterialIcon name={item.icon} className={`text-[22px] ${isActive ? 'text-bk-teal' : ''}`} />
      <span className="text-[10px] font-semibold tracking-[0.01em]">{item.shortLabel}</span>
    </Link>
  )
}
