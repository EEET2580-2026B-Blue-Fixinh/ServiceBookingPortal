import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookingShell } from '../../components/booking/BookingShell'
import { MaterialIcon } from '../../components/shared/icons'
import { ROUTES } from '../../config/constants'
import { useAuth } from '../../context/AuthContext'
import { fetchMyProfile } from '../../services/api/endpoints/customerEndpoints'
import { getInitials } from '../../utils/format'

const DATE_FORMATTER = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' })

const SIGN_IN_LABELS = { PASSWORD: 'Email and password', GOOGLE: 'Google' }

function signInMethodLabel(authProviders) {
  if (!authProviders?.length) return '—'
  return authProviders.map((provider) => SIGN_IN_LABELS[provider] ?? provider).join(' + ')
}

function formatAddress(address) {
  if (!address) return null
  const line1 = [address.streetNumber, address.streetName].filter(Boolean).join(' ')
  const line2 = [address.ward, address.provinceName ?? address.provinceCode].filter(Boolean).join(', ')
  return { line1, line2 }
}

function InfoRow({ icon, children }) {
  return (
    <div className="flex items-center gap-2.5 text-[13.5px] text-bk-text">
      <MaterialIcon name={icon} className="text-[19px] text-bk-muted" />
      {children}
    </div>
  )
}

export function ProfilePage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [profile, setProfile] = useState(null)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchMyProfile()
      .then((profileData) => {
        setProfile(profileData)
        setStatus('ready')
      })
      .catch((requestError) => {
        setError(requestError)
        setStatus('error')
      })
  }, [])

  const handleSignOut = () => {
    logout()
    navigate(ROUTES.LOGIN, { replace: true })
  }

  const address = profile ? formatAddress(profile.address) : null

  return (
    <BookingShell activeNav="profile" breadcrumb="Service Booking Portal" title="Profile & settings">
      {status === 'loading' && <p className="font-mono text-[12.5px] text-bk-muted">Loading profile…</p>}
      {status === 'error' && (
        <p className="rounded-xl border border-bk-red/30 bg-bk-surface p-5 font-semibold text-bk-red">
          {error?.message}
        </p>
      )}

      {status === 'ready' && profile && (
        <div className="grid max-w-[760px] gap-4">
          <div className="flex items-center justify-between gap-3 rounded-xl border border-bk-border bg-bk-surface p-[18px] shadow-sm">
            <div className="flex items-center gap-3">
              <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full bg-bk-teal-soft text-lg font-bold text-bk-teal-strong">
                {getInitials(profile.fullName)}
              </span>
              <div>
                <p className="text-lg font-extrabold text-bk-text">{profile.fullName}</p>
                <p className="text-[13.5px] text-bk-muted">{profile.email}</p>
              </div>
            </div>
            <span className="shrink-0 rounded-full border border-bk-border bg-bk-bg px-3 py-1 font-mono text-[11.5px] font-bold text-bk-muted">
              {profile.customerId}
            </span>
          </div>

          <div className="rounded-xl border border-bk-border bg-bk-surface p-[18px] shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <p className="text-[11.5px] font-bold tracking-[0.04em] text-bk-muted">CONTACT &amp; ADDRESS</p>
              <button
                type="button"
                title="Coming soon"
                className="flex cursor-default items-center gap-1 text-[13px] font-bold text-bk-teal opacity-60"
              >
                <MaterialIcon name="edit" className="text-[17px]" />
                Edit
              </button>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-4 max-[600px]:grid-cols-1">
              <div>
                <p className="text-[12px] text-bk-muted">Email (used to sign in)</p>
                <p className="mt-0.5 font-bold text-bk-text">{profile.email}</p>
              </div>
              <div>
                <p className="text-[12px] text-bk-muted">Phone number</p>
                <p className="mt-0.5 font-bold text-bk-text">{profile.phoneNumber ?? 'Not set'}</p>
              </div>
            </div>

            <div className="mt-4">
              <p className="text-[12px] text-bk-muted">Registered address</p>
              {address ? (
                <p className="mt-0.5 font-bold text-bk-text">
                  {address.line1}
                  <br />
                  {address.line2}
                </p>
              ) : (
                <p className="mt-0.5 text-[13.5px] text-bk-text">
                  No address on file yet.{' '}
                  <Link to={ROUTES.PROFILE_COMPLETION} className="font-bold text-bk-teal hover:underline">
                    Complete your address
                  </Link>
                </p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 max-[600px]:grid-cols-1">
            <div className="rounded-xl border border-bk-border bg-bk-surface p-[18px] shadow-sm">
              <p className="mb-3 text-[11.5px] font-bold tracking-[0.04em] text-bk-muted">ACCOUNT</p>
              <div className="grid gap-3">
                <InfoRow icon="key">
                  Sign-in method: <strong>{signInMethodLabel(profile.authProviders)}</strong>
                </InfoRow>
                <InfoRow icon="calendar_month">
                  Customer since {DATE_FORMATTER.format(new Date(profile.registeredAt))}
                </InfoRow>
              </div>
            </div>

            <div className="rounded-xl border border-bk-border bg-bk-surface p-[18px] shadow-sm">
              <p className="mb-3 text-[11.5px] font-bold tracking-[0.04em] text-bk-muted">THIS DEVICE</p>
              <p className="text-[13px] text-bk-muted">
                Signing out discards your session token on this device. You&apos;ll need to sign in again to see
                your bookings.
              </p>
              <button
                type="button"
                onClick={handleSignOut}
                className="mt-4 flex w-full appearance-none items-center justify-center gap-2 rounded-[9px] border
                  border-bk-border px-3 py-[9px] text-[13.5px] font-bold text-bk-text hover:border-bk-red
                  hover:bg-bk-red-soft hover:text-bk-red"
              >
                <MaterialIcon name="logout" className="text-lg" />
                Sign out
              </button>
            </div>
          </div>
        </div>
      )}
    </BookingShell>
  )
}
