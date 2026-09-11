import { useState } from 'react'
import { createStaff, fetchStaffMember, updateStaff } from '../services/adminStaffEndpoints'

/**
 * Owns the staff form dialog's lifecycle: open (create/edit), submit, close,
 * and both the generic and per-field error state (Req 3.5.S1, 3.1.S5).
 *
 * `onNotice`/`onReload` are how the caller finds out what happened - this
 * hook owns none of the staff list itself, matching useActivateCustomer's
 * split between the mutating hook and the list hook.
 */
export function useStaffForm({ onNotice, onReload }) {
  const [mode, setMode] = useState(null)
  const [initialValues, setInitialValues] = useState(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loadingStaffId, setLoadingStaffId] = useState(null)

  const openCreate = () => {
    setError('')
    setFieldErrors({})
    setInitialValues(null)
    setMode('create')
  }

  const openEdit = async (member) => {
    setError('')
    setFieldErrors({})
    setLoadingStaffId(member.staffId)
    try {
      const detail = await fetchStaffMember(member.staffId)
      setInitialValues(detail)
      setMode('edit')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingStaffId(null)
    }
  }

  const close = () => {
    setMode(null)
    setInitialValues(null)
    setError('')
    setFieldErrors({})
  }

  const submit = async (payload) => {
    const isCreate = mode === 'create'
    setPending(true)
    setError('')
    setFieldErrors({})
    try {
      if (isCreate) {
        await createStaff(payload)
        onNotice({ variant: 'success', message: 'Staff account created. Welcome email sent.' })
      } else {
        await updateStaff(initialValues.staffId, payload)
        onNotice({ variant: 'success', message: 'Staff account updated.' })
      }
      close()
      onReload()
    } catch (err) {
      // API Documentation §10.6: a 502 EMAIL_DELIVERY_FAILED means the staff
      // account already exists in SMP - only the welcome email failed, and
      // it's being retried in the background. Treating it like any other
      // failure would leave the dialog open, and a retried submit would
      // create a second staff account for the same person.
      if (isCreate && err.code === 'EMAIL_DELIVERY_FAILED') {
        onNotice({
          variant: 'warning',
          message: "Account created. Welcome email hasn't gone out yet; we'll keep retrying.",
        })
        close()
        onReload()
      } else {
        // Prefer the backend's per-field messages over one generic banner,
        // same convention as RegisterPage.
        const backendFieldErrors = err.fieldErrorsByField?.() ?? {}
        setFieldErrors(backendFieldErrors)
        if (Object.keys(backendFieldErrors).length === 0) {
          setError(err.message)
        }
      }
    } finally {
      setPending(false)
    }
  }

  return {
    mode,
    initialValues,
    pending,
    error,
    fieldErrors,
    loadingStaffId,
    openCreate,
    openEdit,
    close,
    submit,
  }
}
