import { AppRoutes } from './pages/routes/AppRoutes'
import { ToastStack } from './components/shared/ToastStack'
import './App.css'

function App() {
  return (
    <>
      <AppRoutes />
      {/* Beside the routes, not inside any of them, so a toast shows on every page
          and is not unmounted by navigating away mid-message. */}
      <ToastStack />
    </>
  )
}

export default App
