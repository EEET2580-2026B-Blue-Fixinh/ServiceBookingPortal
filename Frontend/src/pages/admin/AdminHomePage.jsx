import { useAuth } from '../../context/AuthContext'

// Placeholder. It exists so AdminRoute has something to guard — the admin
// features themselves are not built yet.
//
// It prints the signed-in identity on purpose: the useful thing to verify here
// is not the page, it is who the guard let through. A CUSTOMER should never see
// this screen at all.
export function AdminHomePage() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-bk-bg px-6 py-10">
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <header className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold text-bk-text">Admin</h1>
            <p className="text-sm text-bk-muted">Placeholder page — nothing is built here yet.</p>
          </div>
          <button
            type="button"
            onClick={logout}
            className="rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 text-sm
                       font-medium text-bk-text hover:border-bk-teal focus:outline-none
                       focus:ring-[3px] focus:ring-bk-teal-soft"
          >
            Sign out
          </button>
        </header>

        <section className="rounded-[9px] border border-bk-border bg-bk-surface p-5">
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
      </div>
    </div>
  )
}
