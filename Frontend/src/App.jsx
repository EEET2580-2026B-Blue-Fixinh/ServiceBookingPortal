import { AppRoutes } from './pages/routes/AppRoutes'
import { ToastStack } from './components/shared/ToastStack'
import './App.css'

// function App() {
//   const { showToast } = useToast()
//   useEffect(() => {
//     showToast({ tone: 'pri', title: 'Technician scheduled', body: 'ORD-2026-1024 · 24 Aug at 18:00 with Pham Anh Tu.' })
//     showToast({ tone: 'ok', title: 'Booking confirmed', body: 'ORD-2026-1025 is confirmed.' })
//     showToast({ tone: 'danger', title: 'Something failed' })
//   }, [showToast])
//   return (
//     <>
//       <AppRoutes />
//       <ToastStack />
//     </>
//   )
// }

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
