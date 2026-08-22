export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  pending = false,
  onConfirm,
  onCancel,
}) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-[420px] rounded-xl border border-bk-border bg-bk-surface p-6 shadow-lg">
        <h2 className="mb-2 text-lg font-extrabold text-bk-text">{title}</h2>
        <p className="mb-6 text-[14px] text-bk-muted">{description}</p>

        <div className="flex justify-end gap-2.5">
          <button
            type="button"
            onClick={onCancel}
            disabled={pending}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 text-[13.5px] font-bold text-bk-text hover:bg-bk-bg disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            className={`appearance-none rounded-[9px] border px-4 py-2 text-[13.5px] font-bold text-white disabled:opacity-50 ${
              danger ? 'border-bk-red bg-bk-red hover:opacity-90' : 'border-bk-teal bg-bk-teal hover:bg-bk-teal-strong'
            }`}
          >
            {pending ? 'Please wait…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}