import { ConfirmDialog } from '../../../shared/ConfirmDialog'

/** Composes the shared ConfirmDialog with the activate/deactivate copy (Req 3.5.S2). */
export function ActivateConfirmModal({ target, pending, onConfirm, onCancel }) {
  const isActive = target?.accountStatus === 'ACTIVE'

  return (
    <ConfirmDialog
      open={target != null}
      title={isActive ? 'Deactivate this account?' : 'Activate this account?'}
      description={
        isActive
          ? `${target?.fullName} will no longer be able to sign in.`
          : `${target?.fullName} will be able to sign in again.`
      }
      confirmLabel={isActive ? 'Deactivate' : 'Activate'}
      danger={isActive}
      pending={pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  )
}
