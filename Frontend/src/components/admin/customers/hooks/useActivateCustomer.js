import { useState } from 'react'
import { setCustomerStatus } from '../services/adminCustomerEndpoints'

/**
 * Confirm-then-toggle flow for one customer's account status (Req 3.5.S2).
 * `onToggled` is how the caller finds out - this hook owns none of the list.
 */
export function useActivateCustomer(onToggled) {
  const [confirmTarget, setConfirmTarget] = useState(null)
  const [pendingId, setPendingId] = useState(null)
  const [error, setError] = useState('')

  const requestToggle = (customer) => setConfirmTarget(customer)
  const cancel = () => setConfirmTarget(null)

  const confirmToggle = async () => {
    const customer = confirmTarget
    const nextStatus = customer.accountStatus === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE'
    setPendingId(customer.customerId)
    try {
      const updated = await setCustomerStatus(customer.customerId, nextStatus)
      onToggled(updated)
      setConfirmTarget(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setPendingId(null)
    }
  }

  return { confirmTarget, pendingId, error, requestToggle, cancel, confirmToggle }
}
