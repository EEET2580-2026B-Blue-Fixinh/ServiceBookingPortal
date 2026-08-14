// Host and path match API Documentation Section 2.1, but the scheme is http:
// the backend's dev profile ships with `server.ssl.enabled: false`
// (application-dev.yml), so port 8443 serves plain HTTP locally. Pointing at
// https:// here fails during the TLS handshake, not with a readable error.
// Override via VITE_API_URL once the team's self-signed cert is in place.
export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8443/api/v1'

// Manual on/off switch for the demo-account shortcut on the login page.
// Flip VITE_SHOW_DEMO_ACCOUNT in .env (or .env.local for a personal-only
// override) — set it to "false" before shipping to production.
export const SHOW_DEMO_ACCOUNT = import.meta.env.VITE_SHOW_DEMO_ACCOUNT === 'true'
