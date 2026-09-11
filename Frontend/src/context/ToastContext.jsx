import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'

// The application-wide toast queue: the Toast Notifier of Milestone 1, Figure 5.
//
// Anything that wants to tell the customer something briefly — a pushed
// notification, a saved form, a sign-in — calls showToast and is done. The stack
// that draws the queue is mounted once, above the routes, so a toast shows on
// whatever page the customer is on and survives navigation mid-message. That is
// the difference from a toast owned by a page: page-local state unmounts with the
// page, and a notification pushed while the customer clicks "New booking" would
// vanish with it.
//
// This provider owns only the queue and each toast's lifetime. What a tone looks
// like — icon, colour — is the stack's business, so the queue stays free of UI.

const ToastContext = createContext(undefined)

/** How long a toast stays, per the design mockup. Long enough to read two lines. */
const TOAST_LIFETIME_MS = 6_500

// Past this the corner becomes a wall of cards. Older ones give way to newer;
// the newest is the one the customer has not had a chance to read yet.
const MAX_VISIBLE = 4

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  // One pending auto-dismiss per toast, keyed by id, so dismissing by hand can
  // cancel the timer rather than leave it to fire on a toast that is already gone.
  const timersRef = useRef(new Map())
  const nextIdRef = useRef(0)

  const dismissToast = useCallback((id) => {
    const timer = timersRef.current.get(id)
    if (timer !== undefined) {
      clearTimeout(timer)
      timersRef.current.delete(id)
    }
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  /**
   * Adds a toast and schedules its removal.
   *
   * @param {object} toast
   * @param {'ok' | 'pri' | 'info' | 'warn' | 'danger' | 'neutral'} [toast.tone]
   *   the mockup's tone names; unknown or missing falls back to neutral
   * @param {string} toast.title one line, what happened
   * @param {string} [toast.body] one or two lines of detail, optional
   * @param {string} [toast.to] where opening the toast should take the reader. A
   *   route, not a handler: the queue stays free of navigation, and the stack —
   *   which is inside the router — is what performs it. Omitted for a toast with
   *   nowhere to go, which is most of them
   * @param {() => void} [toast.onOpen] run when the reader opens it, before
   *   navigating. For a pushed notification this is what marks it read, so opening
   *   from the toast and from the bell leave the same state behind
   * @returns {number} the toast's id, for a caller that wants to dismiss it early
   */
  const showToast = useCallback(
    ({ tone = 'neutral', title, body, to, onOpen }) => {
      nextIdRef.current += 1
      const id = nextIdRef.current

      // slice(-MAX_VISIBLE) drops the oldest when the corner is full. Its timer is
      // left to fire; dismissing an id that is no longer in the list is a no-op,
      // and clearing it here would mean reading the list inside the updater,
      // which StrictMode runs twice.
      setToasts((current) =>
        [...current, { id, tone, title, body, to, onOpen }].slice(-MAX_VISIBLE),
      )
      timersRef.current.set(
        id,
        setTimeout(() => dismissToast(id), TOAST_LIFETIME_MS),
      )
      return id
    },
    [dismissToast],
  )

  // Nothing may fire after the provider is gone. The Map is captured in the effect
  // body on purpose: a ref read inside the cleanup would see whatever the ref holds
  // at unmount, which for a DOM ref is often null — the react-hooks rule flags the
  // pattern for that reason, and capturing sidesteps it honestly.
  useEffect(() => {
    const timers = timersRef.current
    return () => {
      timers.forEach((timer) => clearTimeout(timer))
      timers.clear()
    }
  }, [])

  const value = { toasts, showToast, dismissToast }

  return <ToastContext.Provider value={value}>{children}</ToastContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider')
  }
  return context
}