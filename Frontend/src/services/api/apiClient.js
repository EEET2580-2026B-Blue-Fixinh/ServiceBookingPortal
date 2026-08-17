import { API_BASE_URL } from '../../config/env'
import { AUTH_TOKEN_KEY, SESSION_ENDING_ERROR_CODES } from '../../config/constants'

// Req A.1.3: one utility centralises REST calls, headers and errors. Components
// call the endpoint modules, never fetch() — so the base URL, the Authorization
// header and the error shape are each defined in exactly one place.

/**
 * A failed request, carrying the backend's error contract (API Documentation
 * Section 2.7) so callers can branch on `code` — never on `message`, which is
 * written for people and will be reworded.
 */
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.error ?? null
    this.fieldErrors = body?.fieldErrors ?? []
    this.traceId = body?.traceId ?? null
  }

  /** The violation for one request field, e.g. `password` — or null if it passed. */
  fieldError(field) {
    return this.fieldErrors.find((violation) => violation.field === field) ?? null
  }

  /** Field errors keyed by field name, ready to merge into a form's error state. */
  fieldErrorsByField() {
    return Object.fromEntries(
      this.fieldErrors.map((violation) => [violation.field, violation.message]),
    )
  }
}

/** Status 0 is reserved for "the request never reached the server". */
export const NETWORK_ERROR_STATUS = 0

// Set by AuthProvider. This module is plain functions and cannot use hooks, so it
// cannot end a session itself — it calls whatever is registered here. One slot
// rather than an event bus: exactly one thing owns "the session ended", the same
// way one thing owns the base URL and the error shape.
let unauthorizedHandler = null

/**
 * Registers the callback that ends the session when the backend rejects our token.
 * Pass null to unregister. AuthProvider is the only intended caller.
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  // Read at call time rather than at module load, so a token stored after this
  // module was first imported is still picked up.
  if (auth) {
    const token = localStorage.getItem(AUTH_TOKEN_KEY)
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    // Offline, DNS failure, CORS rejection, or the backend simply is not running.
    // fetch() rejects without a status, so give callers something uniform.
    throw new ApiError(NETWORK_ERROR_STATUS, {
      message: 'Cannot reach the server. Check that the backend is running and try again.',
    })
  }

  if (response.status === 204) {
    return null
  }

  // Errors are JSON too, but never trust that — a proxy or crash can return HTML.
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    const error = new ApiError(response.status, payload)

    // The token we sent is dead, so the session is over however this call ends.
    // Fired before the throw so it still happens if the caller swallows the error.
    if (error.status === 401 && SESSION_ENDING_ERROR_CODES.includes(error.code)) {
      unauthorizedHandler?.()
    }

    throw error
  }

  return payload
}

export const apiClient = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
}
