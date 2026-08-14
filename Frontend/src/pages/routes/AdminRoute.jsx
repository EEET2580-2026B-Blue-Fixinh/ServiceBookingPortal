import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

// Req A.2.a: staff/customer must not reach admin pages. Same shape as
// ProtectedRoute, but also rejects an authenticated non-admin rather than
// only an unauthenticated visitor.
export function AdminRoute() {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (user?.role !== 'ADMIN') {
    return <Navigate to="/booking" replace />
  }

  return <Outlet />
}
