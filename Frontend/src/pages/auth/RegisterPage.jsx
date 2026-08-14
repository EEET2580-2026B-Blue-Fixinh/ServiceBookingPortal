import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useVietnamAddress } from '../../hooks/useVietnamAddress'
import { register as requestRegister } from '../../services/api/endpoints/authEndpoints'
import { AuthLayout } from '../../components/auth/AuthLayout'
import { AuthIntro } from '../../components/auth/AuthIntro'
import { GoogleIcon, MaterialIcon } from '../../components/shared/icons'
import { SelectField } from '../../components/shared/form/SelectField/SelectField'
import {
  PASSWORD_REQUIREMENTS,
  doPasswordsMatch,
  isValidPassword,
  isValidVietnamesePhone,
} from '../../utils/validators'

const INITIAL_FORM = {
  lastName: '',
  firstName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  streetNumber: '',
  streetName: '',
}

// Which form field a backend fieldErrors[] entry belongs to (Section 2.7).
// Address violations surface on the city select, the only address control that
// can be wrong in a way the customer can act on. The API carries one fullName,
// so a complaint about it is shown against the first of the two name inputs.
const FIELD_BY_BACKEND_NAME = {
  fullName: 'lastName',
  email: 'email',
  phoneNumber: 'phone',
  password: 'password',
  confirmPassword: 'confirmPassword',
  address: 'city',
  'address.provinceCode': 'city',
}

// The API accepts 7-11 digits starting with 0, so the +84 form the validator
// also allows has to be converted before it is sent.
function toLocalPhoneNumber(value) {
  const digitsOnly = value.trim().replace(/[\s.-]/g, '')
  return digitsOnly.startsWith('+84') ? `0${digitsOnly.slice(3)}` : digitsOnly
}

// Vietnamese names are written family name first: "Nguyen Hong Anh" is
// Nguyen (ho) + Hong Anh (ten dem + ten). The API stores the single fullName
// this produces, so the join order is what makes the stored name correct.
function toFullName({ lastName, firstName }) {
  return `${lastName.trim()} ${firstName.trim()}`.trim()
}

export function RegisterPage() {
  const [form, setForm] = useState(INITIAL_FORM)
  const [errors, setErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const address = useVietnamAddress()
  const navigate = useNavigate()

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
    setFormError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    // The email field keeps type="email" so the browser's own parser
    // decides validity (via .validity.valid) — noValidate just stops it
    // from showing the native tooltip, we show our own error text instead.
    const emailInput = event.currentTarget.elements.email

    const nextErrors = {}
    const fullName = toFullName(form)
    if (!form.lastName.trim()) {
      nextErrors.lastName = 'Enter your last name.'
    }
    if (!form.firstName.trim()) {
      nextErrors.firstName = 'Enter your first name.'
    }
    // The API's rule applies to the joined name, not to either half.
    if (form.lastName.trim() && form.firstName.trim() && fullName.length > 100) {
      nextErrors.firstName = 'Your full name must be 100 characters or fewer.'
    }
    if (!emailInput.validity.valid) {
      nextErrors.email = 'Enter a valid email address.'
    }
    if (!isValidVietnamesePhone(form.phone)) {
      nextErrors.phone = 'Enter a valid Vietnamese phone number.'
    }
    if (!isValidPassword(form.password)) {
      nextErrors.password = 'Your password does not meet all requirements yet.'
    }
    if (!doPasswordsMatch(form.password, form.confirmPassword)) {
      nextErrors.confirmPassword = 'Passwords do not match.'
    }
    if (!form.streetNumber.trim()) {
      nextErrors.streetNumber = 'Enter your street number.'
    }
    if (!form.streetName.trim()) {
      nextErrors.streetName = 'Enter your street name.'
    }
    if (!address.city) {
      nextErrors.city = 'Select your city or province.'
    }
    if (!address.ward) {
      nextErrors.ward = 'Select your ward.'
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    setFormError('')
    try {
      await requestRegister({
        email: form.email.trim(),
        phoneNumber: toLocalPhoneNumber(form.phone),
        password: form.password,
        confirmPassword: form.confirmPassword,
        fullName,
        address: {
          streetNumber: form.streetNumber.trim(),
          streetName: form.streetName.trim(),
          ward: address.ward,
          provinceCode: address.cityCode,
        },
      })

      // No token comes back: registration ends at the login screen, which keeps
      // one path for issuing tokens.
      navigate('/login', { replace: true })
    } catch (error) {
      // The backend validates everything again and names each failing field, so
      // prefer its per-field messages over one generic banner.
      const backendFieldErrors = error.fieldErrorsByField?.() ?? {}
      const mapped = {}
      for (const [backendField, message] of Object.entries(backendFieldErrors)) {
        const formField = FIELD_BY_BACKEND_NAME[backendField]
        if (formField) {
          mapped[formField] = message
        }
      }

      setErrors(mapped)
      if (Object.keys(mapped).length === 0) {
        setFormError(error.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  const handleGoogleSignup = () => {
    // TODO: wire up Google OAuth once available
  }

  return (
    <AuthLayout brand={<AuthIntro />}>
      <h2 className="auth-title">Create your account</h2>
      <p className="auth-subtitle">
        All fields are required. We use your address to route the nearest technician.
      </p>

      <form onSubmit={handleSubmit} noValidate>
        {formError && (
          <p className="auth-form-error" role="alert">
            {formError}
          </p>
        )}

        {/* Family name first, the order Vietnamese names are written in — the
            two halves are joined in this order to form the stored fullName. */}
        <div className="auth-row">
          <div className="auth-field">
            <label htmlFor="lastName">Last name</label>
            <input
              id="lastName"
              name="lastName"
              type="text"
              autoComplete="family-name"
              placeholder="Nguyen"
              value={form.lastName}
              onChange={handleChange}
              aria-invalid={Boolean(errors.lastName)}
              className={errors.lastName ? 'auth-input-invalid' : ''}
              required
            />
            {errors.lastName && <p className="auth-error-text">{errors.lastName}</p>}
          </div>
          <div className="auth-field">
            <label htmlFor="firstName">First name</label>
            <input
              id="firstName"
              name="firstName"
              type="text"
              autoComplete="given-name"
              placeholder="Hong Anh"
              value={form.firstName}
              onChange={handleChange}
              aria-invalid={Boolean(errors.firstName)}
              className={errors.firstName ? 'auth-input-invalid' : ''}
              required
            />
            {errors.firstName && <p className="auth-error-text">{errors.firstName}</p>}
          </div>
        </div>

        <div className="auth-field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="Please enter your email"
            value={form.email}
            onChange={handleChange}
            aria-invalid={Boolean(errors.email)}
            className={errors.email ? 'auth-input-invalid' : ''}
            required
          />
          {errors.email && <p className="auth-error-text">{errors.email}</p>}
        </div>

        <div className="auth-field">
          <label htmlFor="phone">Phone number</label>
          <input
            id="phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            placeholder="Please enter your phone number"
            value={form.phone}
            onChange={handleChange}
            aria-invalid={Boolean(errors.phone)}
            className={errors.phone ? 'auth-input-invalid' : ''}
            required
          />
          {errors.phone && <p className="auth-error-text">{errors.phone}</p>}
        </div>

        <div className="auth-row">
          <div className="auth-field">
            <label htmlFor="password">Password</label>
            <div className="auth-password-wrap">
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="At least 8 characters"
                value={form.password}
                onChange={handleChange}
                aria-invalid={Boolean(errors.password)}
                className={errors.password ? 'auth-input-invalid' : ''}
                required
              />
              <button
                type="button"
                className="auth-password-toggle"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                <MaterialIcon name={showPassword ? 'visibility_off' : 'visibility'} />
              </button>
            </div>
            {errors.password && <p className="auth-error-text">{errors.password}</p>}
          </div>
          <div className="auth-field">
            <label htmlFor="confirmPassword">Confirm password</label>
            <div className="auth-password-wrap">
              <input
                id="confirmPassword"
                name="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Repeat password"
                value={form.confirmPassword}
                onChange={handleChange}
                aria-invalid={Boolean(errors.confirmPassword)}
                className={errors.confirmPassword ? 'auth-input-invalid' : ''}
                required
              />
              <button
                type="button"
                className="auth-password-toggle"
                onClick={() => setShowConfirmPassword((prev) => !prev)}
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
              >
                <MaterialIcon name={showConfirmPassword ? 'visibility_off' : 'visibility'} />
              </button>
            </div>
            {errors.confirmPassword && <p className="auth-error-text">{errors.confirmPassword}</p>}
          </div>
        </div>

        <div className="auth-requirements">
          {PASSWORD_REQUIREMENTS.map((requirement) => {
            const met = requirement.test(form.password)
            return (
              <span
                key={requirement.key}
                className={`auth-requirement${met ? ' auth-requirement-met' : ''}`}
              >
                <MaterialIcon name={met ? 'check_circle' : 'radio_button_unchecked'} filled={met} />
                {requirement.label}
              </span>
            )
          })}
        </div>

        <div className="auth-section-label">Service address</div>

        <div className="auth-row">
          <div className="auth-field">
            <label htmlFor="streetNumber">Street number</label>
            <input
              id="streetNumber"
              name="streetNumber"
              type="text"
              placeholder="Street number"
              value={form.streetNumber}
              onChange={handleChange}
              aria-invalid={Boolean(errors.streetNumber)}
              className={errors.streetNumber ? 'auth-input-invalid' : ''}
              required
            />
            {errors.streetNumber && <p className="auth-error-text">{errors.streetNumber}</p>}
          </div>
          <div className="auth-field">
            <label htmlFor="streetName">Street name</label>
            <input
              id="streetName"
              name="streetName"
              type="text"
              placeholder="Street name"
              value={form.streetName}
              onChange={handleChange}
              aria-invalid={Boolean(errors.streetName)}
              className={errors.streetName ? 'auth-input-invalid' : ''}
              required
            />
            {errors.streetName && <p className="auth-error-text">{errors.streetName}</p>}
          </div>
        </div>

        {/* Two levels only: the district tier was abolished in the 2025 reform,
            so wards hang directly off the city / province. */}
        <div className="auth-row">
          <div className="auth-field">
            <label htmlFor="city">City / Province</label>
            <SelectField
              id="city"
              value={address.city}
              onChange={address.handleCityChange}
              options={address.provinces.map((province) => province.provinceName)}
              placeholder={address.cityPlaceholder}
              disabled={address.provincesStatus !== 'ready'}
            />
            {errors.city && <p className="auth-error-text">{errors.city}</p>}
            {address.provincesStatus === 'error' && (
              <p className="auth-field-hint">
                Couldn't load provinces.
                <button type="button" onClick={address.retryProvinces}>
                  Retry
                </button>
              </p>
            )}
          </div>
          <div className="auth-field">
            <label htmlFor="ward">Ward</label>
            <SelectField
              id="ward"
              value={address.ward}
              onChange={address.handleWardChange}
              options={address.wards.map((ward) => ward.name)}
              placeholder={address.wardPlaceholder}
              disabled={!address.city || address.wardsStatus !== 'ready'}
            />
            {errors.ward && <p className="auth-error-text">{errors.ward}</p>}
            {address.wardsStatus === 'error' && (
              <p className="auth-field-hint">
                Couldn't load wards.
                <button type="button" onClick={address.retryWards}>
                  Retry
                </button>
              </p>
            )}
          </div>
        </div>

        <button className="auth-submit" type="submit" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <div className="auth-divider">or</div>

      <button type="button" className="auth-google-btn" onClick={handleGoogleSignup}>
        <GoogleIcon />
        Continue with Google
      </button>

      <p className="auth-switch">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  )
}
