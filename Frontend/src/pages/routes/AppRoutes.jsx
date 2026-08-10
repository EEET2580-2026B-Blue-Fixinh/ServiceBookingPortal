import { Route, Routes } from 'react-router-dom'
import { LoginPage } from '../../components/auth/LoginPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
    </Routes>
  )
}
