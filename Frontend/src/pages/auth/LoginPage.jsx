import { useState } from 'react'
import { Link } from 'react-router-dom'
import { SHOW_DEMO_ACCOUNT } from '../../config/env'
import { AuthLayout } from '../../components/auth/AuthLayout'
import { GoogleIcon } from '../../components/shared/icons'

const DEMO_EMAIL = 'an.nguyen@gmail.com'
const DEMO_PASSWORD = 'Fixinh@2025'

export function LoginPage() {
  const [form, setForm] = useState({ identifier: '', password: '' })

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleFillDemoAccount = () => {
    setForm({ identifier: DEMO_EMAIL, password: DEMO_PASSWORD })
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    // TODO: wire up to authEndpoints once the API client is implemented
  }

  const handleGoogleLogin = () => {
    // TODO: wire up Google OAuth once available
  }

  return (
    <AuthLayout
      brand={
        <>
          <h1>Welcome back. Your repairs, all in one place.</h1>
          <p className="auth-brand-desc">
            Track active bookings, review past jobs, and rate completed services.
          </p>
          <div className="auth-visual">
            <span className="auth-visual-tag">app screenshot</span>
          </div>
        </>
      }
    >
      <h2 className="auth-title">Log in</h2>
      <p className="auth-subtitle">Use your email or phone number.</p>

      <form onSubmit={handleSubmit}>
        <div className="auth-field">
          <label htmlFor="identifier">Email or phone number</label>
          <input
            id="identifier"
            name="identifier"
            type="text"
            autoComplete="username"
            placeholder="Your email or phone number"
            value={form.identifier}
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
            autoComplete="current-password"
            placeholder="Your password"
            value={form.password}
            onChange={handleChange}
            required
          />
        </div>

        <button className="auth-submit" type="submit">
          Log in
        </button>
      </form>

      <div className="auth-divider">or</div>

      <button type="button" className="auth-google-btn" onClick={handleGoogleLogin}>
        <GoogleIcon />
        Continue with Google
      </button>

      {SHOW_DEMO_ACCOUNT && (
        <button type="button" className="auth-demo-box" onClick={handleFillDemoAccount}>
          <span className="auth-demo-label">Demo account</span> — email{' '}
          <code>{DEMO_EMAIL}</code> · password <code>{DEMO_PASSWORD}</code>
          <span className="auth-demo-hint">Click to fill</span>
        </button>
      )}

      <p className="auth-switch">
        New to Fixinh? <Link to="/register">Create an account</Link>
      </p>
    </AuthLayout>
  )
}
