import { MaterialIcon } from '../shared/icons'

export function AuthIntro() {
  return (
    <>
      <h1>
        <span className="auth-headline-break">Repairs</span>{' '}
        <span className="auth-headline-break">booked,</span>{' '}
        <span className="auth-headline-break">tracked, and</span>{' '}
        <span className="auth-headline-break">rated in one</span>{' '}
        <span className="auth-headline-break">place.</span>
      </h1>
      <p className="auth-brand-desc">
        Plumbing, electrical and electronics specialists. Book a visit, follow the
        technician's schedule, and rate the work when it's done.
      </p>
      <ul className="auth-feature-list">
        <li className="auth-feature-item">
          <MaterialIcon name="event_available" />
          Pick the day and the window that suits you
        </li>
        <li className="auth-feature-item">
          <MaterialIcon name="notifications" />
          Live updates when a job is confirmed or scheduled
        </li>
        <li className="auth-feature-item">
          <MaterialIcon name="loyalty" />
          Transparent costs and 5-star ratings after every visit
        </li>
      </ul>
    </>
  )
}
