import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { AUTH_EXPIRES_AT_KEY, AUTH_TOKEN_KEY, AUTH_USER_KEY } from '../config/constants'
import { setUnauthorizedHandler } from '../services/api/apiClient'

// Req 3.2.M1: the backend issues an access token with a fixed lifetime and no
// refresh token, so when it expires the only path forward is signing in again.
// This provider owns that deadline: it is the only module that writes or reads
// AUTH_EXPIRES_AT_KEY, so the arithmetic and the comparison cannot drift apart.

const AuthContext = createContext(undefined)

const AUTH_KEYS = [AUTH_TOKEN_KEY, AUTH_USER_KEY, AUTH_EXPIRES_AT_KEY]

// One frozen object per terminal state. Reusing the same reference is what makes a
// second expireSession() call — from a second request that also 401'd — a no-op:
// setState compares with Object.is, sees no change, and skips the re-render. That
// is why a burst of failures produces one sign-out and one redirect.
const NO_SESSION = Object.freeze({ token: null, user: null, expiresAt: null, expired: false })
const EXPIRED_SESSION = Object.freeze({ ...NO_SESSION, expired: true })

function clearStoredSession() {
  AUTH_KEYS.forEach((key) => localStorage.removeItem(key))
}

/** null means "expiry unknown" — there is no deadline to enforce. */
function parseExpiresAt(raw) {
  const value = Number(raw)
  return Number.isFinite(value) && value > 0 ? value : null
}

/**
 * The session as localStorage has it. Runs in the useState initialiser, before the
 * first render, so a tab opened after the token died never paints a signed-in frame.
 */
function readStoredSession() {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (!token) return NO_SESSION

  const expiresAt = parseExpiresAt(localStorage.getItem(AUTH_EXPIRES_AT_KEY))
  if (expiresAt !== null && expiresAt <= Date.now()) {
    clearStoredSession()
    return EXPIRED_SESSION
  }

  try {
    return {
      token,
      user: JSON.parse(localStorage.getItem(AUTH_USER_KEY)),
      expiresAt,
      expired: false,
    }
  } catch {
    // Hand-edited or half-written storage: start clean rather than throw during render.
    clearStoredSession()
    return NO_SESSION
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readStoredSession)
  const { token, expiresAt } = session

  const logout = useCallback(() => {
    clearStoredSession()
    setSession(NO_SESSION)
  }, [])

  // Same as logout, but remembered as "expired" so the login page can say why.
  const expireSession = useCallback(() => {
    clearStoredSession()
    setSession(EXPIRED_SESSION)
  }, [])

  const login = (nextToken, nextUser, expiresInSeconds) => {
    // The server reports a lifetime in seconds; store the wall-clock deadline so a
    // reload — or a second tab — can tell how much of it is left. Derived from this
    // browser's clock rather than the token's own `exp` claim on purpose: measuring
    // a duration locally means a constant offset against the server cancels out,
    // whereas comparing server time to Date.now() would sign a user whose clock is
    // ten minutes slow out ten minutes early, every session.
    const expiresAt =
      Number.isFinite(expiresInSeconds) && expiresInSeconds > 0
        ? Date.now() + expiresInSeconds * 1000
        : null

    localStorage.setItem(AUTH_TOKEN_KEY, nextToken)
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(nextUser))
    if (expiresAt === null) localStorage.removeItem(AUTH_EXPIRES_AT_KEY)
    else localStorage.setItem(AUTH_EXPIRES_AT_KEY, String(expiresAt))

    setSession({ token: nextToken, user: nextUser, expiresAt, expired: false })
  }

  // Expiry must sign the user out the moment it happens, not on the next API call.
  // Nothing is guaranteed to render or fetch while someone reads a page, so a timer
  // is the only thing that can make it immediate.
  useEffect(() => {
    if (!token || expiresAt === null) return

    // Math.max keeps the delay non-negative, which also covers a deadline that has
    // already passed: the sign-out lands on the next tick rather than as a setState
    // run synchronously inside this effect.
    const timerId = setTimeout(expireSession, Math.max(0, expiresAt - Date.now()))

    // setTimeout is suspended while the machine sleeps and throttled in a tab hidden
    // for more than a few minutes, so it can run late. The deadline is wall-clock, so
    // re-checking on return is always safe and never early.
    const recheck = () => {
      if (Date.now() >= expiresAt) expireSession()
    }
    document.addEventListener('visibilitychange', recheck)

    return () => {
      clearTimeout(timerId)
      document.removeEventListener('visibilitychange', recheck)
    }
  }, [token, expiresAt, expireSession])

  // apiClient cannot use hooks, so it calls back in here when the backend rejects
  // our token. Registered in an effect rather than at module load so the handler is
  // always the live provider's, and is torn down with it.
  useEffect(() => {
    setUnauthorizedHandler(expireSession)
    return () => setUnauthorizedHandler(null)
  }, [expireSession])

  // Presence of a token is enough, because nothing here keeps a dead one: the
  // initialiser drops an already-expired session before the first render, and the
  // timer drops it the moment the deadline passes. Deliberately no Date.now()
  // comparison — render has to be a pure function of state (react-hooks/purity), so
  // the deadline is enforced by those two paths plus the 401 handler, never
  // recomputed mid-render.
  const isAuthenticated = Boolean(token)

  const value = {
    token,
    user: session.user,
    isAuthenticated,
    sessionExpired: session.expired,
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
