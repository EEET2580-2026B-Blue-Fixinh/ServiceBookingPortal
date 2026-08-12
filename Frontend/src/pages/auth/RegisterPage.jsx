import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useVietnamAddress } from '../../hooks/useVietnamAddress'
import { AuthLayout } from '../../components/auth/AuthLayout'
import { GoogleIcon, MaterialIcon } from '../../components/shared/icons'
import { SelectField } from '../../components/shared/form/SelectField/SelectField'

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
  const address = useVietnamAddress()

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    // TODO: wire up to authEndpoints once the API client is implemented
  }

  const handleGoogleSignup = () => {
    // TODO: wire up Google OAuth once available
  }

  return (
    <AuthLayout
      backTo="/login"
      brand={
        <>
          <h1>Join thousands of households who fix it the easy way.</h1>
          <div className="auth-testimonial">
            <div className="auth-stars">
              {Array.from({ length: 5 }).map((_, index) => (
                <MaterialIcon key={index} name="star" filled />
              ))}
            </div>
            <p className="auth-quote">
              "Booked an electrician on Monday, fixed by Tuesday. Tracking and the
              cost breakdown made it completely stress-free."
            </p>
            <p className="auth-quote-author">— Mai, Đà Nẵng</p>
          </div>
        </>
      }
    >
      <h2 className="auth-title">Create your account</h2>
      <p className="auth-subtitle">
        It takes about a minute. Fields marked are all required.
      </p>

      <form onSubmit={handleSubmit}>
        <div className="auth-field">
          <label htmlFor="email">Email address</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="Your email address"
            value={form.email}
            onChange={handleChange}
            required
          />
        </div>

        <div className="auth-field">
          <label htmlFor="phone">Phone number</label>
          <input
            id="phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            placeholder="Your phone number"
            value={form.phone}
            onChange={handleChange}
            required
          />
        </div>

        <div className="auth-field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            placeholder="Create a strong password"
            value={form.password}
            onChange={handleChange}
            required
          />
        </div>

        <div className="auth-field">
          <label htmlFor="confirmPassword">Confirm password</label>
          <input
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            placeholder="Re-enter your password"
            value={form.confirmPassword}
            onChange={handleChange}
            required
          />
        </div>

        <div className="auth-section-label">
          <MaterialIcon name="location_on" />
          Home address
        </div>

        <div className="auth-row">
          <div className="auth-field">
            <label htmlFor="streetNumber">Street number</label>
            <input
              id="streetNumber"
              name="streetNumber"
              type="text"
              placeholder="Your street number"
              value={form.streetNumber}
              onChange={handleChange}
              required
            />
          </div>
          <div className="auth-field">
            <label htmlFor="streetName">Street name</label>
            <input
              id="streetName"
              name="streetName"
              type="text"
              placeholder="Your street name"
              value={form.streetName}
              onChange={handleChange}
              required
            />
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
