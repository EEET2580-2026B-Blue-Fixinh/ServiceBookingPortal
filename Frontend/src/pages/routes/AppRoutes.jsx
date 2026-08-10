import { Route, Routes } from 'react-router-dom'
import { LoginPage } from '../auth/LoginPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
    </Routes>
  )
}
