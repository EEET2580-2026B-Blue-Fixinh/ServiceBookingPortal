import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Must stay 3000 — it's the only origin the backend's CORS policy
    // admits (API Documentation v1.0, Section 2.1 / Req. A.2.6).
    port: 3000,
  },
})
