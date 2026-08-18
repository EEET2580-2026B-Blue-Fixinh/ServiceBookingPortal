import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ROLES, ROUTES } from '../../config/constants'
import { useAuth } from '../../context/AuthContext'

// Req A.2.a: staff/customer must not reach admin pages. Same shape as
// ProtectedRoute, but also rejects an authenticated non-admin rather than
// only an unauthenticated visitor.
export function AdminRoute() {
  const { isAuthenticated, sessionExpired, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.LOGIN} replace state={{ from: location, sessionExpired }} />
  }

  if (user?.role !== ROLES.ADMIN) {
    return <Navigate to={ROUTES.BOOKING} replace />
  }

  return <Outlet />
}
