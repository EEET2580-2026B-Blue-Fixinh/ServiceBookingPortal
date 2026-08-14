import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../auth/LoginPage'
import { RegisterPage } from '../auth/RegisterPage'
import { ProtectedRoute } from './ProtectedRoute'
import BookingPage from '../booking/BookingPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Signed-in only. A booking belongs to a customer, so the API refuses
          these calls without a token anyway — this just means the visitor is
          sent to sign in instead of meeting a page that cannot load. */}
      <Route element={<ProtectedRoute />}>
        <Route path="/booking" element={<BookingPage />} />
      </Route>
    </Routes>
  )
}
