import { Navigate, Route, Routes } from 'react-router-dom'
import { ROUTES } from '../../config/constants'
import { LoginPage } from '../auth/LoginPage'
import { RegisterPage } from '../auth/RegisterPage'
import { ProfileCompletionPage } from '../auth/ProfileCompletionPage'
import { ProtectedRoute } from './ProtectedRoute'
import { AdminRoute } from './AdminRoute'
import { MyBookingsPage } from '../booking/MyBookingsPage'
import { AdminCustomersPage } from '../admin/AdminCustomersPage'
import { AdminStaffPage } from '../admin/AdminStaffPage'
import { AdminHomePage } from '../admin/AdminHomePage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.LOGIN} replace />} />
      <Route path={ROUTES.LOGIN} element={<LoginPage />} />
      <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
      
      {/* Google OAuth profile completion (Req 3.2.U3). Public but expects
          session data from LoginPage — navigating directly just redirects. */}
      <Route path={ROUTES.PROFILE_COMPLETION} element={<ProfileCompletionPage />} />

      {/* Signed-in only. A booking belongs to a customer, so the API refuses
          these calls without a token anyway — this just means the visitor is
          sent to sign in instead of meeting a page that cannot load. */}
      <Route element={<ProtectedRoute />}>
        <Route path={ROUTES.BOOKING} element={<MyBookingsPage />} />
      </Route>

      {/* Admins only. AdminRoute turns away a signed-in CUSTOMER as well as an
          unauthenticated visitor, so this is a stricter nesting than above —
          not an addition to it. */}
      <Route element={<AdminRoute />}>
        <Route path={ROUTES.ADMIN} element={<AdminHomePage />} />
        <Route path="/admin/customers" element={<AdminCustomersPage />} />
        <Route path="/admin/staff" element={<AdminStaffPage />} />
      </Route>
    </Routes>
  )
}
