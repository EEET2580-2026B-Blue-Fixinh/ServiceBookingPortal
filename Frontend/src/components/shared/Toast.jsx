import { useEffect, useState } from 'react'

const AUTO_DISMISS_MS = 4000
// Matches the transition-duration below — the element must stay mounted long
// enough for the fade-out to actually play before onDismiss unmounts it.
const EXIT_DURATION_MS = 200

/**
 * A single toast: pops in, sits for AUTO_DISMISS_MS, then fades out and calls
 * onDismiss. Reuses the app's existing entrance animation (animate-fx-in,
 * index.css) rather than defining a second one just for the fade-in half.
 */
export function Toast({ message, onDismiss }) {
  const [closing, setClosing] = useState(false)

  useEffect(() => {
    const dismissTimer = setTimeout(() => setClosing(true), AUTO_DISMISS_MS)
    return () => clearTimeout(dismissTimer)
  }, [])

  useEffect(() => {
    if (!closing) return
    const removeTimer = setTimeout(onDismiss, EXIT_DURATION_MS)
    return () => clearTimeout(removeTimer)
  }, [closing, onDismiss])

  return (
    // Two layers on purpose: this outer one owns the fixed, horizontally-centred
    // position (a static transform), so the inner element's own transform —
    // driven by animate-fx-in and the exit transition below — never fights it
    // for the same CSS property.
    <div className="fixed top-6 left-1/2 z-[200] -translate-x-1/2">
      <div
        role="status"
        className={`flex items-center gap-3 rounded-lg bg-bk-teal-soft px-4 py-3 text-sm
          font-semibold text-bk-teal-strong shadow-lg transition-all duration-200
          ${closing ? 'translate-y-[-8px] opacity-0' : 'animate-fx-in opacity-100'}`}
      >
        {message}
        <button
          type="button"
          onClick={() => setClosing(true)}
          aria-label="Dismiss"
          className="text-bk-teal-strong/70 hover:text-bk-teal-strong"
        >
          <span aria-hidden="true">&times;</span>
        </button>
      </div>
    </div>
  )
}
