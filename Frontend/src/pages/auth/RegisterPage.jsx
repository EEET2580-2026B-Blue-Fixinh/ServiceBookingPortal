import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useVietnamAddress } from '../../hooks/useVietnamAddress'
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
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  streetNumber: '',
  streetName: '',
}

export function RegisterPage() {
  const [form, setForm] = useState(INITIAL_FORM)
  const [errors, setErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const address = useVietnamAddress()

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
  }

  const handleSubmit = (event) => {
    event.preventDefault()

    // The email field keeps type="email" so the browser's own parser
    // decides validity (via .validity.valid) — noValidate just stops it
    // from showing the native tooltip, we show our own error text instead.
    const emailInput = event.currentTarget.elements.email

    const nextErrors = {}
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
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    // TODO: wire up to authEndpoints once the API client is implemented
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

        <div className="auth-row">
          <div className="auth-field">
            <label htmlFor="city">City / Province</label>
            <SelectField
              id="city"
              value={address.city}
              onChange={address.handleCityChange}
              options={address.provinces.map((province) => province.name)}
              placeholder={address.cityPlaceholder}
              disabled={address.provincesStatus !== 'ready'}
            />
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
            <label htmlFor="district">District</label>
            <SelectField
              id="district"
              value={address.district}
              onChange={address.handleDistrictChange}
              options={address.districts.map((district) => district.name)}
              placeholder={address.districtPlaceholder}
              disabled={!address.city || address.districtsStatus !== 'ready'}
            />
            {address.districtsStatus === 'error' && (
              <p className="auth-field-hint">
                Couldn't load districts.
                <button type="button" onClick={address.retryDistricts}>
                  Retry
                </button>
              </p>
            )}
          </div>
        </div>

        <div className="auth-field">
          <label htmlFor="ward">Ward</label>
          <SelectField
            id="ward"
            value={address.ward}
            onChange={address.handleWardChange}
            options={address.wards.map((ward) => ward.name)}
            placeholder={address.wardPlaceholder}
            disabled={!address.district}
          />
        </div>

        <button className="auth-submit" type="submit">
          Create account
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
