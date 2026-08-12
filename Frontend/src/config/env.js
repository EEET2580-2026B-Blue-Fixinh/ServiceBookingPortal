export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

// Manual on/off switch for the demo-account shortcut on the login page.
// Flip VITE_SHOW_DEMO_ACCOUNT in .env (or .env.local for a personal-only
// override) — set it to "false" before shipping to production.
export const SHOW_DEMO_ACCOUNT = import.meta.env.VITE_SHOW_DEMO_ACCOUNT === 'true'
