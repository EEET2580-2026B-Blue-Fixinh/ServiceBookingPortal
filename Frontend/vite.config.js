import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    // sockjs-client is written for Node-style bundlers and reads `global.location`
    // and `global.document` at import time. Vite provides neither `global` nor a
    // polyfill, so without this the app fails on load with
    // "ReferenceError: global is not defined" — the one runtime error that
    // `pnpm build` cannot catch. Aliasing to globalThis costs nothing else.
    global: 'globalThis',
  },
  server: {
    // Must stay 3000 — it's the only origin the backend's CORS policy
    // admits (API Documentation v1.0, Section 2.1 / Req. A.2.6).
    port: 3000,
  },
})
