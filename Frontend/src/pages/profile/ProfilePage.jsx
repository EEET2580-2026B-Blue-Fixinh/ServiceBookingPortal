import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BookingShell } from '../../components/booking/BookingShell'
import { MaterialIcon } from '../../components/shared/icons'
import { SelectField } from '../../components/shared/form/SelectField/SelectField'
import { ROUTES } from '../../config/constants'
import { useAuth } from '../../context/AuthContext'
import { useVietnamAddress } from '../../hooks/useVietnamAddress'
import { fetchMyProfile, updateMyProfile } from '../../services/api/endpoints/customerEndpoints'
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

function editFormFrom(profile) {
  return {
    fullName: profile.fullName ?? '',
    phoneNumber: profile.phoneNumber ?? '',
    streetNumber: profile.address?.streetNumber ?? '',
    streetName: profile.address?.streetName ?? '',
  }
}

function InfoRow({ icon, children }) {
  return (
    <div className="flex items-center gap-2.5 text-[13.5px] text-bk-text">
      <MaterialIcon name={icon} className="text-[19px] text-bk-muted" />
      {children}
    </div>
  )
}

function inputClass(hasError) {
  return (
    'w-full rounded-[9px] border bg-white px-3 py-2.5 font-[inherit] text-[14px] text-bk-text focus:outline-none focus:ring-[3px] ' +
    (hasError
      ? 'border-bk-red focus:ring-bk-red-soft'
      : 'border-bk-border focus:border-bk-teal focus:ring-bk-teal-soft')
  )
}

function FormField({ label, htmlFor, error, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-[12px] font-semibold text-bk-muted">
        {label}
      </label>
      {children}
      {error && <p className="text-[12px] font-semibold text-bk-red">{error}</p>}
    </div>
  )
}

export function ProfilePage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [profile, setProfile] = useState(null)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState(null)

  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // The ward already on the profile, shown until the user picks a different one from the
  // dropdown — it does not wait on the ward list to load, so editing opens with the saved
  // address visible immediately rather than a blank field while wards fetch.
  const [pendingWard, setPendingWard] = useState(null)

  const {
    city, cityCode, ward, provinces, wards,
    provincesStatus, wardsStatus, cityPlaceholder, wardPlaceholder,
    handleCityChange, handleWardChange,
  } = useVietnamAddress()

  // Once the user has picked a ward from the (now loaded) dropdown, that choice wins over the
  // stale pre-fill.
  const effectiveWard = ward || pendingWard || ''

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

  const startEditing = () => {
    setForm(editFormFrom(profile))
    setFieldErrors({})
    setFormError('')
    setPendingWard(profile.address?.ward ?? null)
    if (profile.address?.provinceName) {
      handleCityChange(profile.address.provinceName)
    }
    setEditing(true)
  }

  const cancelEditing = () => {
    setEditing(false)
    setForm(null)
    setFieldErrors({})
    setFormError('')
    setPendingWard(null)
  }

  const handleFieldChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setFieldErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
    setFormError('')
  }

  const onCityChange = (value) => {
    handleCityChange(value)
    setPendingWard(null)
    setFieldErrors((prev) => ({ ...prev, 'address.provinceCode': undefined, address: undefined }))
    setFormError('')
  }

  const onWardChange = (value) => {
    handleWardChange(value)
    setPendingWard(null)
    setFieldErrors((prev) => ({ ...prev, address: undefined }))
    setFormError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    setSubmitting(true)
    setFormError('')

    try {
      const updated = await updateMyProfile({
        fullName: form.fullName.trim(),
        phoneNumber: form.phoneNumber.trim(),
        address: {
          streetNumber: form.streetNumber.trim(),
          streetName: form.streetName.trim(),
          ward: effectiveWard,
          provinceCode: cityCode,
        },
      })
      setProfile(updated)
      setEditing(false)
      setForm(null)
    } catch (requestError) {
      if (requestError.code === 'PHONE_ALREADY_REGISTERED') {
        setFieldErrors((prev) => ({
          ...prev,
          phoneNumber: 'Another account already uses this phone number.',
        }))
      } else if (requestError.fieldErrors?.length) {
        setFieldErrors(requestError.fieldErrorsByField())
      } else {
        setFormError(requestError.message || 'Failed to update profile. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const address = profile ? formatAddress(profile.address) : null
  const provinceNames = provinces.map((province) => province.provinceName)
  const wardNames = wards.map((wardOption) => wardOption.name)

  return (
    <BookingShell activeNav="profile" breadcrumb="Service Booking Portal" title="Profile & settings">
      {status === 'loading' && <p className="font-mono text-[12.5px] text-bk-muted">Loading profile…</p>}
      {status === 'error' && (
        <p className="rounded-xl border border-bk-red/30 bg-bk-surface p-5 font-semibold text-bk-red">
          {error?.message}
        </p>
      )}

      {/* Centred on purpose: the column is narrower than the frame's content container, and
          left as a plain max-width it would hug the left edge of a wide screen. Every grid
          inside sizes itself with auto-fit rather than a breakpoint, so the same markup
          stacks on a phone, pairs up on a tablet and never needs a pixel value revisited. */}
      {status === 'ready' && profile && (
        <div className="mx-auto grid w-full max-w-[720px] gap-4">
          {/* Wraps rather than spreads: a 24-character id that may not shrink, next to a name
             that may not shrink, used to force this card — and with it every card in the
             column — wider than a phone. The id drops to its own line instead. */}
          <div className="flex flex-wrap items-center gap-3.5 rounded-xl border border-bk-border bg-bk-surface p-[18px] shadow-sm">
            <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full bg-bk-teal-soft text-lg font-bold text-bk-teal-strong">
              {getInitials(profile.fullName)}
            </span>
            <div className="min-w-0 flex-1 basis-[180px]">
              <p className="truncate text-lg font-extrabold text-bk-text">{profile.fullName}</p>
              <p className="truncate text-[13.5px] text-bk-muted">{profile.email}</p>
            </div>
            <span className="max-w-full truncate rounded-full border border-bk-border bg-bk-bg px-3 py-1 font-mono text-[11.5px] font-bold text-bk-muted">
              {profile.customerId}
            </span>
          </div>

          <div
            className={`rounded-xl border bg-bk-surface p-[18px] shadow-sm ${
              editing ? 'border-bk-teal' : 'border-bk-border'
            }`}
          >
            <div className="flex items-center justify-between gap-3">
              <p
                className={`text-[11.5px] font-bold tracking-[0.04em] ${
                  editing ? 'text-bk-teal-strong' : 'text-bk-muted'
                }`}
              >
                {editing ? 'EDITING CONTACT & ADDRESS' : 'CONTACT & ADDRESS'}
              </p>
              {!editing && (
                <button
                  type="button"
                  onClick={startEditing}
                  className="flex items-center gap-1 text-[13px] font-bold text-bk-teal hover:underline"
                >
                  <MaterialIcon name="edit" className="text-[17px]" />
                  Edit
                </button>
              )}
            </div>

            {editing ? (
              <form onSubmit={handleSubmit} className="mt-4 grid gap-4" noValidate>
                {formError && (
                  <p className="rounded-lg bg-bk-red-soft px-3 py-2 text-[13px] font-semibold text-bk-red" role="alert">
                    {formError}
                  </p>
                )}

                <FormField label="Full name" htmlFor="profile-fullName" error={fieldErrors.fullName}>
                  <input
                    id="profile-fullName"
                    name="fullName"
                    type="text"
                    value={form.fullName}
                    onChange={handleFieldChange}
                    className={inputClass(Boolean(fieldErrors.fullName))}
                    required
                  />
                </FormField>

                <FormField label="Phone number" htmlFor="profile-phoneNumber" error={fieldErrors.phoneNumber}>
                  <input
                    id="profile-phoneNumber"
                    name="phoneNumber"
                    type="text"
                    autoComplete="tel"
                    value={form.phoneNumber}
                    onChange={handleFieldChange}
                    className={inputClass(Boolean(fieldErrors.phoneNumber))}
                    required
                  />
                </FormField>

                <div className="grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] gap-3.5">
                  <FormField label="Street number" htmlFor="profile-streetNumber">
                    <input
                      id="profile-streetNumber"
                      name="streetNumber"
                      type="text"
                      value={form.streetNumber}
                      onChange={handleFieldChange}
                      className={inputClass(Boolean(fieldErrors.address))}
                      required
                    />
                  </FormField>
                  <FormField label="Street name" htmlFor="profile-streetName">
                    <input
                      id="profile-streetName"
                      name="streetName"
                      type="text"
                      value={form.streetName}
                      onChange={handleFieldChange}
                      className={inputClass(Boolean(fieldErrors.address))}
                      required
                    />
                  </FormField>
                  <FormField label="Ward" htmlFor="profile-ward">
                    <SelectField
                      id="profile-ward"
                      value={effectiveWard}
                      onChange={onWardChange}
                      options={wardNames}
                      placeholder={wardPlaceholder}
                      disabled={!city || wardsStatus !== 'ready'}
                    />
                  </FormField>
                </div>
                {fieldErrors.address && (
                  <p className="-mt-2 text-[12px] font-semibold text-bk-red">{fieldErrors.address}</p>
                )}

                <FormField
                  label="City / Province"
                  htmlFor="profile-city"
                  error={fieldErrors['address.provinceCode']}
                >
                  <SelectField
                    id="profile-city"
                    value={city}
                    onChange={onCityChange}
                    options={provinceNames}
                    placeholder={cityPlaceholder}
                    disabled={provincesStatus !== 'ready'}
                  />
                </FormField>

                <div className="flex items-center gap-2.5">
                  <button
                    type="submit"
                    disabled={submitting}
                    className="rounded-[9px] bg-bk-teal px-4 py-2.5 text-[13.5px] font-bold text-white hover:bg-bk-teal-strong disabled:opacity-60"
                  >
                    {submitting ? 'Saving…' : 'Save changes'}
                  </button>
                  <button
                    type="button"
                    onClick={cancelEditing}
                    disabled={submitting}
                    className="rounded-[9px] border border-bk-border px-4 py-2.5 text-[13.5px] font-bold text-bk-text hover:bg-bk-bg disabled:opacity-60"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            ) : (
              <>
                <div className="mt-4 grid grid-cols-[repeat(auto-fit,minmax(200px,1fr))] gap-4">
                  <div className="min-w-0">
                    <p className="text-[12px] text-bk-muted">Email (used to sign in)</p>
                    <p className="mt-0.5 break-words font-bold text-bk-text">{profile.email}</p>
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
                    <p className="mt-0.5 text-[13.5px] text-bk-text">No address on file yet.</p>
                  )}
                </div>
              </>
            )}
          </div>

          <div className="grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-4">
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
