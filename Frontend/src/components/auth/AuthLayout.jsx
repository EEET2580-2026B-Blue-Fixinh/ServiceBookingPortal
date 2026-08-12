import { Link } from 'react-router-dom'
import { MaterialIcon } from '../shared/icons'
import './AuthLayout.css'

function BrandMark({ className = '' }) {
  return (
    <span className={`auth-logo ${className}`}>
      <span className="auth-logo-mark">
        <MaterialIcon name="handyman" className="auth-logo-icon" />
      </span>
      Fixinh
    </span>
  )
}

export function AuthLayout({ brand, backTo = '/', children }) {
  return (
    <div className="auth-split">
      <aside className="auth-brand">
        <span className="auth-blob auth-blob-a" aria-hidden="true" />
        <span className="auth-blob auth-blob-b" aria-hidden="true" />
        <div className="auth-brand-header">
          <BrandMark />
        </div>
        <div className="auth-brand-body">{brand}</div>
      </aside>

      <main className="auth-panel">
        <div className="auth-panel-topbar">
          <Link to={backTo} className="auth-back-link">
            <MaterialIcon name="arrow_back" className="auth-back-icon" />
            Back
          </Link>
          <BrandMark className="auth-logo-compact" />
        </div>
        <div className="auth-panel-body">{children}</div>
      </main>
    </div>
  )
}
