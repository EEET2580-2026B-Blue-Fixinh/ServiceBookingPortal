import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { WS_URL } from '../config/env'

// The real-time channel of API Documentation Section 9.3: STOMP over SockJS, used
// only to receive notifications pushed to the signed-in customer.
//
// This is the one module in the app that imports @stomp/stompjs or sockjs-client.
// Components and Context see a function that opens the channel and a handle that
// closes it; frames, destinations, reconnection and the token header are dealt
// with here, so swapping the transport or the library later touches one file.
//
// Nothing is ever sent. The backend refuses SEND frames outright, and the only
// subscription it accepts is the caller's own queue below.

/** The one destination the backend allows a client to subscribe to. */
const NOTIFICATIONS_DESTINATION = '/user/queue/notifications'

// Exponential backoff between reconnect attempts: 1 s, 2 s, 4 s ... capped at 30 s.
// A backend restart takes a few seconds, so the first retries are quick; a backend
// that stays down is asked every half minute, not every second.
const INITIAL_RECONNECT_DELAY_MS = 1_000
const MAX_RECONNECT_DELAY_MS = 30_000

/**
 * A pushed message parsed from its frame, or null when the body is not the JSON
 * object Section 9.3 promises. A malformed push is a backend defect, not something
 * the customer can act on, so it is dropped rather than surfaced.
 */
function parsePush(frame) {
  try {
    const parsed = JSON.parse(frame.body)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

/**
 * Opens the channel and keeps it open until {@link disconnect} is called.
 *
 * The token is read through `getToken` on every connect attempt, including
 * reconnects, rather than captured once: the session silently rotates its access
 * token, and a reconnect after a backend restart must carry the current one, not
 * the one that was valid when the app loaded.
 *
 * A session that is already open is not re-authenticated when the token rotates or
 * is revoked elsewhere — it re-authenticates on its next connect. A revoked
 * session ends through the HTTP path: the next REST call is refused, AuthProvider
 * ends the session, and the owner of this handle disconnects.
 *
 * @param {object} options
 * @param {() => string | null | undefined} options.getToken the current access
 *   token, or nothing when there is no session
 * @param {(message: object) => void} options.onMessage called with each pushed
 *   Section 9.3 message: notificationId, type, message, bookingRef, createdAt
 * @param {(info: { reconnected: boolean }) => void} [options.onConnected] called on
 *   every successful CONNECT; `reconnected` is false the first time and true after
 *   a drop, which is the moment to catch up on anything missed via Section 9.1
 * @param {(code: string) => void} [options.onRejected] called with the error code
 *   from a STOMP ERROR frame — AUTH_TOKEN_EXPIRED, AUTH_TOKEN_REVOKED, and so on.
 *   The channel keeps retrying with backoff regardless, because the next attempt
 *   reads the token afresh and a rotated one may well be accepted
 * @returns {{ disconnect: () => void }} a handle; disconnect closes the socket and
 *   stops reconnection, which is what an explicit sign-out needs
 */
export function connectWebSocket({ getToken, onMessage, onConnected, onRejected }) {
  let connectedBefore = false

  const client = new Client({
    // SockJS picks the transport: WebSocket where it can, HTTP fallbacks where it
    // cannot. The handshake itself carries no token — SockJS cannot set headers on
    // it — which is why the token goes in the CONNECT frame below instead.
    webSocketFactory: () => new SockJS(WS_URL),

    reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
    maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,

    // The backend sends no heartbeats and expects none; SockJS keeps the transport
    // alive on its own. Asking for them would make the client wait for frames that
    // never come and drop a perfectly good connection.
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,

    // Runs before every CONNECT, so a reconnect always carries the token that is
    // current now. Without a token the CONNECT goes out bare and the backend
    // refuses it — the correct outcome for a session that ended mid-retry.
    beforeConnect: () => {
      const token = getToken()
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },

    // Subscriptions do not survive a reconnect; the library documents that they
    // must be made here, on every connect, and not once after activate().
    onConnect: () => {
      client.subscribe(NOTIFICATIONS_DESTINATION, (frame) => {
        const message = parsePush(frame)
        if (message) onMessage(message)
      })
      onConnected?.({ reconnected: connectedBefore })
      connectedBefore = true
    },

    // An ERROR frame from the backend. Its message header carries the error code,
    // never prose (Section 2.7). The server closes the socket right after, and the
    // library treats that close like any other drop, so reconnection continues.
    onStompError: (frame) => {
      onRejected?.(frame.headers.message)
    },

    // The library logs every frame through this when given a function. Left silent;
    // replace with console.debug locally when tracing a connection problem.
    debug: () => {},
  })

  client.activate()

  return {
    disconnect: () => {
      // Closes the socket and, more importantly, marks the client inactive so no
      // reconnect is scheduled. This is the difference between "the connection
      // dropped" and "the customer signed out".
      client.deactivate()
    },
  }
}
