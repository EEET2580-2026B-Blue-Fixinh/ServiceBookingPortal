import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ROUTES } from '../../config/constants'
import { useAuth } from '../../context/AuthContext'

export function ProtectedRoute() {
  const { isAuthenticated, sessionExpired } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    // sessionExpired separates "your session ran out" from "you were never signed
    // in" — the login page only apologises for the first. `from` is what sends the
    // user back to the page they were reading once they sign in again.
    return <Navigate to={ROUTES.LOGIN} replace state={{ from: location, sessionExpired }} />
  }

  return <Outlet />
}
