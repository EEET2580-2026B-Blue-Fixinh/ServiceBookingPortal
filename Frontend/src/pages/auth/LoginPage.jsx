import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { DEMO_ACCOUNTS, SHOW_DEMO_ACCOUNT } from '../../config/env'
import { ROUTES, SESSION_EXPIRED_MESSAGE } from '../../config/constants'
import { AuthLayout } from '../../components/auth/AuthLayout'
import { AuthIntro } from '../../components/auth/AuthIntro'
import { GoogleIcon, MaterialIcon } from '../../components/shared/icons'
import { isValidLoginIdentifier } from '../../utils/validators'
import { useAuth } from '../../context/AuthContext'
import { login as requestLogin } from '../../services/api/endpoints/authEndpoints'

// Hidden rather than broken when a developer has not copied .env.example: without
// an address the buttons would fill blanks and the sign-in would fail confusingly.
const CAN_SHOW_DEMO_ACCOUNT = SHOW_DEMO_ACCOUNT && Boolean(DEMO_ACCOUNTS.customer.email)

export function LoginPage() {
  // Declared before the state below, which seeds itself from location.state.
  const location = useLocation()

  const [form, setForm] = useState({ identifier: '', password: '' })
  const [errors, setErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)
  // Seeded once from the redirect that sent us here, so a forced sign-out explains
  // itself in the alert this page already renders. handleChange clears it as soon
  // as the user starts typing.
  const [formError, setFormError] = useState(
    location.state?.sessionExpired ? SESSION_EXPIRED_MESSAGE : '',
  )
  const [submitting, setSubmitting] = useState(false)

  const { login: startSession } = useAuth()
  const navigate = useNavigate()

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
    setFormError('')
  }

  const handleFillDemoAccount = (role) => {
    setErrors({})
    setFormError('')
    const account = role === 'admin' ? DEMO_ACCOUNTS.admin : DEMO_ACCOUNTS.customer
    setForm({ identifier: account.email, password: account.password })
  }

  const handleSubmit = async (event) => {
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

    setSubmitting(true)
    setFormError('')
    try {
      const session = await requestLogin({
        identifier: form.identifier.trim(),
        password: form.password,
      })

      // expiresIn is the token's lifetime in seconds; the context turns it into the
      // deadline it enforces, so a forced sign-out can happen without an API call.
      startSession(session.accessToken, session.user, session.expiresIn)

      // Back to whatever ProtectedRoute bounced them away from, or to the
      // booking page as the default landing spot after signing in.
      const intended = location.state?.from?.pathname
      navigate(intended ?? ROUTES.BOOKING, { replace: true })
    } catch (error) {
      // The backend answers an unknown identifier and a wrong password
      // identically on purpose, so there is one message to show for both.
      setFormError(error.message)
    } finally {
      setSubmitting(false)
    }
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
        {formError && (
          <p className="auth-form-error" role="alert">
            {formError}
          </p>
        )}

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

        <button className="auth-submit" type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
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

      {CAN_SHOW_DEMO_ACCOUNT && (
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
