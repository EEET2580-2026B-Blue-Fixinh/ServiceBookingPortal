import { useNavigate } from 'react-router-dom'
import { MaterialIcon } from './icons'
import { useToast } from '../../context/ToastContext'

// Draws the toast queue as the design mockup does: a column pinned to the
// bottom-right corner, newest at the bottom, each card an icon, a title, an
// optional body and a close button, bordered in the tone's colour. Mounted once,
// above the routes, in App.
//
// Deliberately not a rework of the existing Toast component. That one is owned by
// each page that renders it and sits top-centre; this one is owned by the app and
// sits where the mockup puts it. The two coexist until the pages move their
// success messages over to useToast, which is their branches' change to make.

/**
 * The mockup's tones, mapped onto the palette in index.css. `neutral` is the
 * fallback for anything unrecognised, so a typo in a caller degrades to a plain
 * card rather than a broken one.
 */
const TONES = {
  ok: { icon: 'check_circle', color: 'text-bk-green', border: 'border-bk-green' },
  pri: { icon: 'event_available', color: 'text-bk-teal', border: 'border-bk-teal' },
  info: { icon: 'info', color: 'text-bk-blue', border: 'border-bk-blue' },
  warn: { icon: 'warning', color: 'text-bk-amber', border: 'border-bk-amber' },
  danger: { icon: 'error', color: 'text-bk-red', border: 'border-bk-red' },
  neutral: { icon: 'info', color: 'text-bk-muted', border: 'border-bk-muted' },
}

/**
 * The icon and the text. A button when the toast has somewhere to go, a plain
 * span otherwise.
 *
 * Two shapes rather than one always-a-button shape: a button that does nothing
 * still takes focus and still announces itself as a button, which is a promise
 * broken for anyone navigating by keyboard. And the close button has to stay
 * outside whichever of the two this returns -- a button inside a button is
 * invalid HTML, and browsers resolve it by dropping one of them.
 */
function ToastFace({ toast, onOpen }) {
  const tone = TONES[toast.tone] ?? TONES.neutral

  const face = (
    <>
      <MaterialIcon name={tone.icon} className={`text-[19px] leading-none ${tone.color}`} />
      <span className="min-w-0 flex-1">
        <span className="block text-[13px] font-semibold text-bk-text">{toast.title}</span>
        {toast.body && (
          <span className="mt-[2px] block text-xs text-bk-muted">{toast.body}</span>
        )}
      </span>
    </>
  )

  if (!toast.to) {
    return <span className="flex min-w-0 flex-1 items-start gap-[10px]">{face}</span>
  }

  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex min-w-0 flex-1 appearance-none items-start gap-[10px] bg-transparent
        text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-bk-teal
        focus-visible:ring-offset-2 focus-visible:ring-offset-bk-surface"
    >
      {face}
    </button>
  )
}

function ToastCard({ toast, onOpen, onDismiss }) {
  const tone = TONES[toast.tone] ?? TONES.neutral

  return (
    <div
      // role="status" announces the card politely when it appears, the same
      // contract the page-level Toast already makes with screen readers.
      role="status"
      // text-left is not decoration: #root centres text globally (index.css), and
      // the page layouts reset it on their own containers -- this stack is a direct
      // child of #root and sits outside every one of them.
      className={`animate-fx-toast pointer-events-auto flex max-w-[340px] items-start gap-[10px]
        rounded-[12px] border bg-bk-surface px-[14px] py-3 text-left shadow-lg ${tone.border}`}
    >
      <ToastFace toast={toast} onOpen={onOpen} />
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="flex text-bk-muted hover:text-bk-text"
      >
        <MaterialIcon name="close" className="text-[17px] leading-none" />
      </button>
    </div>
  )
}

export function ToastStack() {
  const { toasts, dismissToast } = useToast()
  const navigate = useNavigate()

  // Opening runs the toast's own hook first -- for a pushed notification that is
  // what marks it read -- then navigates, then clears the card. Dismissing last,
  // so a failure in either of the first two leaves the toast on screen to try
  // again rather than disappearing having done half the job.
  const openToast = (toast) => {
    toast.onOpen?.()
    navigate(toast.to)
    dismissToast(toast.id)
  }

  if (toasts.length === 0) return null

  return (
    // pointer-events-none on the column so the empty space beside a card never
    // blocks a click on what is underneath; each card turns them back on.
    <div
      className="pointer-events-none fixed bottom-[18px] right-[18px] z-[200] flex flex-col
        items-end gap-[10px]"
    >
      {toasts.map((toast) => (
        <ToastCard
          key={toast.id}
          toast={toast}
          onOpen={() => openToast(toast)}
          onDismiss={() => dismissToast(toast.id)}
        />
      ))}
    </div>
  )
}