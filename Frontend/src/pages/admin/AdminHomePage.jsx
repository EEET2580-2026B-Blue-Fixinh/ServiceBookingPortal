import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { AdminShell } from '../../components/admin/AdminShell'
import { MaterialIcon } from '../../components/shared/icons'
import { Toast } from '../../components/shared/Toast'
import { useAuth } from '../../context/AuthContext'

const QUICK_LINKS = [
  {
    to: '/admin/customers',
    icon: 'group',
    title: 'Customer accounts',
    description: 'View customers and activate or deactivate their accounts.',
  },
  {
    to: '/admin/staff',
    icon: 'engineering',
    title: 'Staff & technicians',
    description: 'Create, edit, and search Receptionist/Technician accounts.',
  },
]

function QuickLinkCard({ to, icon, title, description }) {
  return (
    <Link
      to={to}
      className="flex items-start gap-3.5 rounded-xl border border-bk-border bg-bk-surface p-[18px]
        no-underline hover:border-bk-teal hover:bg-bk-teal-soft"
    >
      <span className="flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-full bg-bk-teal-soft text-bk-teal-strong">
        <MaterialIcon name={icon} className="text-[19px]" />
      </span>
      <span className="flex flex-col">
        <span className="font-bold text-bk-text">{title}</span>
        <span className="text-[13px] text-bk-muted">{description}</span>
      </span>
    </Link>
  )
}

export function AdminHomePage() {
  const { user } = useAuth()
  const location = useLocation()
  const [showWelcome, setShowWelcome] = useState(Boolean(location.state?.justSignedIn))

  return (
    <AdminShell activeNav="dashboard" breadcrumb="Admin Portal" title="Dashboard">
      {showWelcome && (
        <Toast
          message={`Welcome back${user?.fullName ? `, ${user.fullName}` : ""}!`}
          onDismiss={() => setShowWelcome(false)}
        />
      )}
      <div className="mb-[18px] grid grid-cols-2 gap-3.5 max-[700px]:grid-cols-1">
        {QUICK_LINKS.map((link) => (
          <QuickLinkCard key={link.to} {...link} />
        ))}
      </div>

      <section className="rounded-xl border border-bk-border bg-bk-surface p-5">
        <h2 className="mb-3 text-sm font-semibold text-bk-text">Signed in as</h2>
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          <dt className="text-bk-muted">Name</dt>
          <dd className="text-bk-text">{user?.fullName ?? '—'}</dd>
          <dt className="text-bk-muted">Email</dt>
          <dd className="text-bk-text">{user?.email ?? '—'}</dd>
          <dt className="text-bk-muted">Role</dt>
          <dd className="font-medium text-bk-teal">{user?.role ?? '—'}</dd>
        </dl>
      </section>
    </AdminShell>
  )
}
