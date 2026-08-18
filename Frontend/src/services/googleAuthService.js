import { GOOGLE_CLIENT_ID, GOOGLE_REDIRECT_URI } from '../../config/env'

/**
 * Google Identity Services wrapper (Req 3.2.U1).
 *
 * Wraps the global `google.accounts.id` API loaded from accounts.google.com/gsi/client.
 * The button renders itself via `renderButton`, and when the user consents, Google
 * redirects them back with an authorization code in the URL hash.
 */

/**
 * Initializes the Google Identity Services client.
 *
 * @param {function(string): void} callback  Called with the authorization code
 *        when Google redirects back after the user consents.
 */
export function initializeGoogleSignIn(callback) {
  if (!window.google || !window.google.accounts) {
    console.error('Google Identity Services SDK not loaded')
    return
  }

  if (!GOOGLE_CLIENT_ID) {
    console.warn('VITE_GOOGLE_CLIENT_ID not configured - Google Sign-In disabled')
    return
  }

  window.google.accounts.id.initialize({
    client_id: GOOGLE_CLIENT_ID,
    callback: (response) => {
      // Google returns an ID token via response.credential, but we're using
      // OAuth 2.0 authorization code flow instead for backend verification.
      // This callback receives the credential (ID token), but we'll use
      // the code flow with redirect instead.
      if (response.credential) {
        callback(response.credential)
      }
    },
    ux_mode: 'redirect',
    redirect_uri: GOOGLE_REDIRECT_URI,
  })
}

/**
 * Renders the Google Sign-In button in the specified container.
 *
 * @param {HTMLElement} element  The container element to render the button into.
 * @param {object} options  Button customization options (theme, size, text, etc.)
 */
export function renderGoogleButton(element, options = {}) {
  if (!window.google || !window.google.accounts) {
    console.error('Google Identity Services SDK not loaded')
    return
  }

  if (!GOOGLE_CLIENT_ID) {
    return // Silently skip rendering if no client ID configured
  }

  window.google.accounts.id.renderButton(element, {
    theme: options.theme || 'outline',
    size: options.size || 'large',
    text: options.text || 'signin_with',
    shape: options.shape || 'rectangular',
    logo_alignment: options.logoAlignment || 'left',
    width: options.width || 350,
    ...options,
  })
}

/**
 * Prompts the user to sign in with Google (alternative to button).
 * Shows the One Tap prompt if the user hasn't dismissed it before.
 */
export function promptGoogleSignIn() {
  if (!window.google || !window.google.accounts) {
    console.error('Google Identity Services SDK not loaded')
    return
  }

  if (!GOOGLE_CLIENT_ID) {
    return
  }

  window.google.accounts.id.prompt()
}

/**
 * Checks if Google Sign-In is available and configured.
 *
 * @returns {boolean}  True if the SDK is loaded and a client ID is configured.
 */
export function isGoogleSignInAvailable() {
  return !!(window.google && window.google.accounts && GOOGLE_CLIENT_ID)
}
