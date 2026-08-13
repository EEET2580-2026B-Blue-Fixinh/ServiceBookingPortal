import { useState } from 'react'
import { Link } from 'react-router-dom'
import { SHOW_DEMO_ACCOUNT } from '../../config/env'
import { AuthLayout } from '../../components/auth/AuthLayout'
import { AuthIntro } from '../../components/auth/AuthIntro'
import { GoogleIcon, MaterialIcon } from '../../components/shared/icons'
import { isValidLoginIdentifier } from '../../utils/validators'

const DEMO_CUSTOMER_EMAIL = 'an.nguyen@gmail.com'
const DEMO_CUSTOMER_PASSWORD = 'Fixinh@2025'
const DEMO_ADMIN_EMAIL = 'admin@fixinh.vn'
const DEMO_ADMIN_PASSWORD = 'Admin@2025'

export function LoginPage() {
  const [form, setForm] = useState({ identifier: '', password: '' })
  const [errors, setErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
  }

  const handleFillDemoAccount = (role) => {
    setErrors({})
    if (role === 'admin') {
      setForm({ identifier: DEMO_ADMIN_EMAIL, password: DEMO_ADMIN_PASSWORD })
    } else {
      setForm({ identifier: DEMO_CUSTOMER_EMAIL, password: DEMO_CUSTOMER_PASSWORD })
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()

    const nextErrors = {}
    if (!isValidLoginIdentifier(form.identifier)) {
      nextErrors.identifier = 'Enter a valid email or phone number.'
    }
    if (!form.password) {
      nextErrors.password = 'Enter your password.'
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    // TODO: wire up to authEndpoints once the API client is implemented
  }

  const handleGoogleLogin = () => {
    // TODO: wire up Google OAuth once available
  }

  const handleForgotPassword = () => {
    // TODO: wire up forgot-password flow once available
  }

  return (
    <AuthLayout brand={<AuthIntro />}>
      <h2 className="auth-title">Sign in</h2>
      <p className="auth-subtitle">Use the email or phone number on your Fixinh account.</p>

      <form onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label htmlFor="identifier">Email or phone number</label>
          <input
            id="identifier"
            name="identifier"
            type="text"
            autoComplete="username"
            placeholder="Please enter your email or phone number"
            value={form.identifier}
            onChange={handleChange}
            aria-invalid={Boolean(errors.identifier)}
            className={errors.identifier ? 'auth-input-invalid' : ''}
            required
          />
          {errors.identifier && <p className="auth-error-text">{errors.identifier}</p>}
        </div>

        <div className="auth-field">
          <div className="auth-label-row">
            <label htmlFor="password">Password</label>
            <button type="button" className="auth-forgot-link" onClick={handleForgotPassword}>
              Forgot?
            </button>
          </div>
          <div className="auth-password-wrap">
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Please enter your password"
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

        <button className="auth-submit" type="submit">
          Sign in
        </button>
      </form>

      <div className="auth-divider">or</div>

      <button type="button" className="auth-google-btn" onClick={handleGoogleLogin}>
        <GoogleIcon />
        Continue with Google
      </button>

      <p className="auth-switch">
        New to Fixinh? <Link to="/register">Create an account</Link>
      </p>

      {SHOW_DEMO_ACCOUNT && (
        <div className="auth-demo-box">
          <span className="auth-demo-label">Demo accounts</span>
          <div className="auth-demo-buttons">
            <button
              type="button"
              className="auth-demo-btn"
              onClick={() => handleFillDemoAccount('customer')}
            >
              <MaterialIcon name="person" />
              Customer
            </button>
            <button
              type="button"
              className="auth-demo-btn"
              onClick={() => handleFillDemoAccount('admin')}
            >
              <MaterialIcon name="admin_panel_settings" />
              Administrator
            </button>
          </div>
        </div>
      )}
    </AuthLayout>
  )
}
