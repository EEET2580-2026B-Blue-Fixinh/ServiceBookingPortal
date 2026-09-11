import { Link, useNavigate } from 'react-router-dom'
import { MaterialIcon } from '../shared/icons'
import { useAuth } from '../../context/AuthContext'

const NAV_ITEMS = [
  { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', to: '/admin' },
  { key: 'customers', label: 'Customers', icon: 'group', to: '/admin/customers' },
  { key: 'staff', label: 'Staff & technicians', icon: 'engineering', to: '/admin/staff' },
]

function getInitials(fullName) {
  if (!fullName) return '?'
  const [first, ...rest] = fullName.trim().split(/\s+/)
  const last = rest.at(-1)
  return ((first?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase()
}

const NAV_ITEM_BASE =
  'flex w-full items-center gap-3 rounded-[9px] px-3 py-2.5 font-semibold no-underline max-[900px]:whitespace-nowrap'

function NavItem({ item, isActive }) {
  const iconClass = `text-xl ${isActive ? 'text-bk-teal' : 'text-bk-muted'}`
  const content = (
    <>
      <MaterialIcon name={item.icon} className={iconClass} />
      {item.label}
    </>
  )

  if (item.to) {
    return (
      <Link
        to={item.to}
        className={`${NAV_ITEM_BASE} ${
          isActive ? 'bg-bk-teal-soft text-bk-teal-strong' : 'text-bk-text hover:bg-bk-bg'
        }`}
      >
        {content}
      </Link>
    )
  }

  return (
    <button
      type="button"
      className={`${NAV_ITEM_BASE} appearance-none cursor-default text-bk-muted`}
    >
      {content}
      <span className="ml-auto rounded-full border border-bk-border bg-bk-bg px-[7px] py-px text-[10px] font-bold tracking-[0.04em] text-bk-muted max-[900px]:hidden">
       
      </span>
    </button>
  )
}

export function AdminShell({ activeNav, breadcrumb, title, headerActions, children }) {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const handleSignOut = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div
      className="fixed inset-0 z-[100] grid grid-cols-[260px_minmax(0,1fr)] bg-bk-bg text-left text-[15px]
        leading-[1.45] text-bk-text max-[900px]:static max-[900px]:grid-cols-1 max-[900px]:min-h-svh
        [--auth-border:var(--color-bk-border)] [--auth-muted:var(--color-bk-muted)]
        [--auth-teal:var(--color-bk-teal)] [--auth-text:var(--color-bk-text)]"
    >
      <aside
        className="flex flex-col gap-0 overflow-y-auto border-r border-bk-border bg-bk-surface p-[22px_18px]
          max-[900px]:flex-row max-[900px]:items-center max-[900px]:overflow-x-auto max-[900px]:overflow-y-visible
          max-[900px]:p-3 max-[900px]:px-4"
      >
        <div className="flex items-center gap-2.5 px-2 pb-[22px] max-[900px]:pb-0 max-[900px]:pl-0 max-[900px]:pr-3">
          <span className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[10px] bg-bk-teal text-white">
            <MaterialIcon name="handyman" />
          </span>
          <span className="text-lg font-extrabold tracking-[-0.2px]">Fixinh</span>
          <span className="rounded-full border border-bk-border bg-bk-bg px-[7px] py-0.5 text-[10.5px] font-bold tracking-[0.04em] text-bk-muted">
            Admin
          </span>
        </div>

        <nav className="flex flex-col gap-0.5 max-[900px]:flex-row">
          {NAV_ITEMS.map((item) => (
            <NavItem key={item.key} item={item} isActive={item.key === activeNav} />
          ))}
        </nav>

        <div className="mt-auto flex flex-col gap-3 rounded-[10px] border border-bk-border p-3 max-[900px]:hidden">
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

      <div className="flex min-w-0 flex-col overflow-hidden">
        <header className="flex shrink-0 items-center justify-between gap-4 border-b border-bk-border bg-bk-surface px-[clamp(20px,3vw,40px)] py-[18px] max-[900px]:flex-wrap">
          <div>
            <p className="mb-0.5 text-[12.5px] text-bk-muted">{breadcrumb}</p>
            <h1 className="m-0 text-2xl font-extrabold tracking-[-0.3px] text-bk-text">{title}</h1>
          </div>
          {headerActions && <div className="flex shrink-0 items-center gap-2.5">{headerActions}</div>}
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto px-[clamp(20px,3vw,40px)] pb-10 pt-[clamp(16px,3vw,28px)]">
          {children}
        </div>
      </div>
    </div>
  )
}
