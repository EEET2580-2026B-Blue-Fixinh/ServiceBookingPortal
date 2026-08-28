import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import {
  AUTH_EXPIRES_AT_KEY,
  AUTH_REFRESH_TOKEN_KEY,
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
} from '../config/constants'
import { setUnauthorizedHandler } from '../services/api/apiClient'
import { logout as requestLogout, refresh as requestRefresh } from '../services/api/endpoints/authEndpoints'

// The access token has a fixed lifetime; the refresh token is what lets a session
// outlive it without asking the user to sign in again. This provider owns both
// deadlines: it is the only module that writes or reads AUTH_EXPIRES_AT_KEY and
// AUTH_REFRESH_TOKEN_KEY, so the arithmetic and the comparison cannot drift apart.

const AuthContext = createContext(undefined)

const AUTH_KEYS = [AUTH_TOKEN_KEY, AUTH_REFRESH_TOKEN_KEY, AUTH_USER_KEY, AUTH_EXPIRES_AT_KEY]

// Refresh this many milliseconds before the access token would actually expire, so
// the round trip to /auth/refresh completes while the old token is still technically
// valid rather than racing its deadline.
const REFRESH_LEAD_MS = 30_000

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

  // Guards against a timer and a visibilitychange recheck both firing near the same
  // moment: the refresh token rotates on every use and the backend treats a reused
  // one as theft, revoking the session — so two concurrent refresh calls would sign
  // the user out instead of silently extending things.
  const refreshingRef = useRef(false)

  const logout = useCallback(() => {
    // Best-effort: fired without waiting on it, so a user closing their laptop
    // mid-request is never blocked from clearing their own local session. The
    // server-side blacklist entry not landing just means this token dies naturally
    // at its own expiry instead of immediately — not a broken sign-out.
    requestLogout().catch(() => {})
    clearStoredSession()
    setSession(NO_SESSION)
  }, [])

  // Same as logout, but remembered as "expired" so the login page can say why.
  const expireSession = useCallback(() => {
    clearStoredSession()
    setSession(EXPIRED_SESSION)
  }, [])

  const login = useCallback((nextToken, nextRefreshToken, nextUser, expiresInSeconds) => {
    // The server reports a lifetime in seconds; store the wall-clock deadline so a
    // reload — or a second tab — can tell how much of it is left. Derived from this
    // browser's clock rather than the token's own `exp` claim on purpose: measuring
    // a duration locally means a constant offset against the server cancels out,
    // whereas comparing server time to Date.now() would sign a user whose clock is
    // ten minutes slow out ten minutes early, every session.
    const nextExpiresAt =
      Number.isFinite(expiresInSeconds) && expiresInSeconds > 0
        ? Date.now() + expiresInSeconds * 1000
        : null

    localStorage.setItem(AUTH_TOKEN_KEY, nextToken)
    localStorage.setItem(AUTH_REFRESH_TOKEN_KEY, nextRefreshToken)
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(nextUser))
    if (nextExpiresAt === null) localStorage.removeItem(AUTH_EXPIRES_AT_KEY)
    else localStorage.setItem(AUTH_EXPIRES_AT_KEY, String(nextExpiresAt))

    setSession({ token: nextToken, user: nextUser, expiresAt: nextExpiresAt, expired: false })
  }, [])

  // Tries to extend the session with the stored refresh token before giving up on
  // it. Falls back to expireSession() whenever that is not possible — no refresh
  // token on hand, or the backend rejects the one stored (already rotated away,
  // expired, or the account was deactivated).
  const attemptRefresh = useCallback(async () => {
    if (refreshingRef.current) return
    const storedRefreshToken = localStorage.getItem(AUTH_REFRESH_TOKEN_KEY)
    if (!storedRefreshToken) {
      expireSession()
      return
    }

    refreshingRef.current = true
    try {
      const renewed = await requestRefresh(storedRefreshToken)
      login(renewed.accessToken, renewed.refreshToken, renewed.user, renewed.expiresIn)
    } catch {
      expireSession()
    } finally {
      refreshingRef.current = false
    }
  }, [expireSession, login])

  // A silent refresh must happen before the access token's deadline, not after —
  // nothing is guaranteed to render or fetch while someone just reads a page, so a
  // timer is the only thing that can make it happen without a user-visible gap.
  useEffect(() => {
    if (!token || expiresAt === null) return

    // Math.max keeps the delay non-negative, which also covers a deadline that has
    // already passed: the refresh attempt lands on the next tick rather than as a
    // setState run synchronously inside this effect.
    const timerId = setTimeout(attemptRefresh, Math.max(0, expiresAt - REFRESH_LEAD_MS - Date.now()))

    // setTimeout is suspended while the machine sleeps and throttled in a tab hidden
    // for more than a few minutes, so it can run late. The deadline is wall-clock, so
    // re-checking on return is always safe and never early.
    const recheck = () => {
      if (Date.now() >= expiresAt - REFRESH_LEAD_MS) attemptRefresh()
    }
    document.addEventListener('visibilitychange', recheck)

    return () => {
      clearTimeout(timerId)
      document.removeEventListener('visibilitychange', recheck)
    }
  }, [token, expiresAt, attemptRefresh])

  // apiClient cannot use hooks, so it calls back in here when the backend rejects
  // our token (including AUTH_TOKEN_REVOKED, e.g. this same account signed out from
  // another tab). Registered in an effect rather than at module load so the handler
  // is always the live provider's, and is torn down with it.
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
