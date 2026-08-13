// Default matches API Documentation v1.0 Section 2.1. The backend runs on a
// self-signed HTTPS cert in dev — trust it locally, or override via
// VITE_API_URL in .env if you need to point elsewhere.
export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'https://localhost:8443/api/v1'

// Manual on/off switch for the demo-account shortcut on the login page.
// Flip VITE_SHOW_DEMO_ACCOUNT in .env (or .env.local for a personal-only
// override) — set it to "false" before shipping to production.
export const SHOW_DEMO_ACCOUNT = import.meta.env.VITE_SHOW_DEMO_ACCOUNT === 'true'
