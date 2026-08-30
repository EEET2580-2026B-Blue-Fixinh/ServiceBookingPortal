import { useEffect, useRef, useState } from 'react'
import { MaterialIcon } from '../shared/icons'

// The rules Section 7.4 states, mirrored from BookingCancelValidator on purpose.
//
// Req 3.1.M4's principle applies here as much as it does to registration: validate in the
// browser so the customer finds out immediately, and again on the server because the
// browser cannot be trusted. The backend stays authoritative -- these numbers exist to save
// a round trip and to say which file is the problem, not to decide whether it is one.
const REASON_MIN_LENGTH = 10
const REASON_MAX_LENGTH = 500
const MAX_IMAGES = 3
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/jpg', 'image/png']

function formatBytes(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/**
 * Which past status this booking is heading for, so the dialog can name it.
 *
 * Section 7.4: from Working a customer is stopping a technician who is already on site,
 * which the API records as a termination rather than a cancellation. The customer should
 * read that before confirming, not discover it in the status badge afterwards.
 */
function outcomeFor(status) {
  return status === 'WORKING'
    ? {
        title: 'Terminate this booking?',
        note: 'A technician is already on site, so this is recorded as a termination rather than a cancellation.',
        confirmLabel: 'Terminate booking',
      }
    : {
        title: 'Cancel this booking?',
        note: 'The booking moves to your past bookings. This cannot be undone.',
        confirmLabel: 'Cancel booking',
      }
}

/**
 * Collects the reason and evidence Req 3.3.M1 requires, then hands them to onConfirm.
 *
 * Owns nothing about the request itself: the page submits, so the page is where a failure
 * is held and where the list is refreshed from. `error` and `pending` come back down.
 */
export function CancelBookingDialog({
  booking,
  pending = false,
  error = null,
  onConfirm,
  onClose,
}) {
  const [reason, setReason] = useState('')
  const [files, setFiles] = useState([])
  const [fileError, setFileError] = useState(null)
  const reasonRef = useRef(null)
  const fileInputRef = useRef(null)

  // Focus the field the customer has to fill in. Without this the dialog opens with focus
  // still on the Cancel button behind it, which a keyboard user cannot see.
  useEffect(() => {
    reasonRef.current?.focus()
  }, [])

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape' && !pending) onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose, pending])

  if (!booking) return null

  const outcome = outcomeFor(booking.status)
  const trimmedLength = reason.trim().length
  const reasonTooShort = trimmedLength < REASON_MIN_LENGTH
  const reasonTooLong = trimmedLength > REASON_MAX_LENGTH
  const canSubmit = !pending && !reasonTooShort && !reasonTooLong

  const addFiles = (incoming) => {
    setFileError(null)
    const combined = [...files, ...incoming]

    if (combined.length > MAX_IMAGES) {
      setFileError(`At most ${MAX_IMAGES} images.`)
      return
    }
    const oversized = combined.find((file) => file.size > MAX_IMAGE_BYTES)
    if (oversized) {
      setFileError(`${oversized.name} is ${formatBytes(oversized.size)} — the limit is 5 MB.`)
      return
    }
    const wrongType = combined.find((file) => !ACCEPTED_TYPES.includes(file.type))
    if (wrongType) {
      setFileError(`${wrongType.name} is not a JPEG or PNG.`)
      return
    }

    setFiles(combined)
  }

  const removeFile = (index) => {
    setFiles((current) => current.filter((_, position) => position !== index))
    setFileError(null)
  }

  return (
    <div
      className="fixed inset-0 z-[200] flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-booking-title"
    >
      <div className="max-h-full w-full max-w-[520px] overflow-y-auto rounded-xl border border-bk-border bg-bk-surface p-6 shadow-lg">
        <h2
          id="cancel-booking-title"
          className="mb-1 text-lg font-extrabold text-bk-text"
        >
          {outcome.title}
        </h2>
        <p className="mb-1 text-[13.5px] text-bk-muted">
          {booking.serviceName}{' '}
          <span className="font-mono">{booking.bookingRef}</span>
        </p>
        <p className="mb-5 text-[13px] text-bk-muted">{outcome.note}</p>

        {error && (
          <p
            className="mb-4 rounded-lg bg-bk-red-soft px-4 py-3 text-[13.5px] font-semibold text-bk-red"
            role="alert"
          >
            {/* Section 2.7: the backend writes the wording, so it is shown rather than
                replaced with a guess that would drift from the real rule. */}
            {error.message}
          </p>
        )}

        <label
          htmlFor="cancel-reason"
          className="mb-1.5 block text-[12.5px] font-semibold text-bk-muted"
        >
          Why are you cancelling?
        </label>
        <textarea
          id="cancel-reason"
          ref={reasonRef}
          rows={4}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          disabled={pending}
          placeholder="The leak stopped by itself and I no longer need the visit."
          className="w-full resize-y rounded-[9px] border border-bk-border bg-white px-3 py-2.5 font-[inherit] text-[14px] text-bk-text focus:border-bk-teal focus:outline-none focus:ring-[3px] focus:ring-bk-teal-soft disabled:opacity-60"
        />
        <p
          className={`mt-1 mb-4 text-[12px] ${
            reasonTooLong ? 'font-semibold text-bk-red' : 'text-bk-muted'
          }`}
        >
          {trimmedLength < REASON_MIN_LENGTH
            ? `${REASON_MIN_LENGTH - trimmedLength} more character${
                REASON_MIN_LENGTH - trimmedLength === 1 ? '' : 's'
              } needed`
            : `${trimmedLength} / ${REASON_MAX_LENGTH}`}
        </p>

        <div className="mb-5">
          <p className="mb-1.5 text-[12.5px] font-semibold text-bk-muted">
            Photos (optional, up to {MAX_IMAGES})
          </p>

          {files.length > 0 && (
            <ul className="mb-2 flex flex-col gap-1.5">
              {files.map((file, index) => (
                <li
                  key={`${file.name}-${file.lastModified}`}
                  className="flex items-center gap-2 rounded-[9px] border border-bk-border bg-bk-bg px-3 py-2 text-[13px] text-bk-text"
                >
                  <MaterialIcon name="image" className="text-[18px] text-bk-muted" />
                  <span className="min-w-0 flex-1 truncate">{file.name}</span>
                  <span className="shrink-0 text-[12px] text-bk-muted">
                    {formatBytes(file.size)}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    disabled={pending}
                    aria-label={`Remove ${file.name}`}
                    className="shrink-0 text-bk-muted hover:text-bk-red disabled:opacity-50"
                  >
                    <MaterialIcon name="close" className="text-[18px]" />
                  </button>
                </li>
              ))}
            </ul>
          )}

          {files.length < MAX_IMAGES && (
            <>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png"
                multiple
                hidden
                onChange={(event) => {
                  addFiles([...event.target.files])
                  // Cleared so choosing the same file twice in a row still fires onChange.
                  event.target.value = ''
                }}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={pending}
                className="flex appearance-none items-center gap-1.5 rounded-[9px] border border-dashed border-bk-border bg-bk-surface px-4 py-2 text-[13.5px] font-bold text-bk-teal hover:border-bk-teal hover:bg-bk-teal-soft disabled:opacity-50"
              >
                <MaterialIcon name="add_photo_alternate" className="text-[19px]" />
                Add photos
              </button>
            </>
          )}

          {fileError && (
            <p className="mt-2 text-[12.5px] font-semibold text-bk-red" role="alert">
              {fileError}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2.5">
          <button
            type="button"
            onClick={onClose}
            disabled={pending}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 text-[13.5px] font-bold text-bk-text hover:bg-bk-bg disabled:opacity-50"
          >
            Keep booking
          </button>
          <button
            type="button"
            onClick={() => onConfirm({ reason: reason.trim(), evidence: files })}
            disabled={!canSubmit}
            className="appearance-none rounded-[9px] border border-bk-red bg-bk-red px-4 py-2 text-[13.5px] font-bold text-white hover:opacity-90 disabled:opacity-50"
          >
            {pending ? 'Please wait…' : outcome.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
