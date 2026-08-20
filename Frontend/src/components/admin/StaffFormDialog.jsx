import { useEffect, useState } from 'react'
import { useVietnamAddress } from '../../hooks/useVietnamAddress'
import { SelectField } from '../shared/form/SelectField/SelectField'

const ROLE_OPTIONS = ['Technician', 'Receptionist']
const STATUS_OPTIONS = ['Active', 'Inactive']

const roleToApi = (label) => (label === 'Technician' ? 'TECHNICIAN' : 'RECEPTIONIST')
const roleToLabel = (value) => (value === 'TECHNICIAN' ? 'Technician' : 'Receptionist')
const statusToApi = (label) => (label === 'Active' ? 'ACTIVE' : 'INACTIVE')
const statusToLabel = (value) => (value === 'INACTIVE' ? 'Inactive' : 'Active')

const EMPTY_FORM = {
  fullName: '',
  email: '',
  phoneNumber: '',
  staffRole: 'Technician',
  streetNumber: '',
  streetName: '',
  employmentStatus: 'Active',
}

const FIELD_CLASS =
  'rounded-[9px] border border-bk-border px-3 py-2.5 text-bk-text focus:border-bk-teal focus:outline-none ' +
  'focus:ring-[3px] focus:ring-bk-teal-soft disabled:bg-bk-bg disabled:text-bk-muted'
const LABEL_CLASS = 'text-[12.5px] font-semibold text-bk-muted'

export function StaffFormDialog({ open, mode, initialValues, pending, error, onSubmit, onCancel }) {
  const isEdit = mode === 'edit'
  const address = useVietnamAddress()
  const [form, setForm] = useState(EMPTY_FORM)
  const [prefilled, setPrefilled] = useState(false)

  // Resets the form whenever the dialog closes, so the next open (create or edit,
  // for a different staff member) never shows stale data from the last session.
  /* eslint-disable react-hooks/set-state-in-effect -- syncing local form state to
     the open/mode/initialValues props is exactly what this effect is for */
  useEffect(() => {
    if (!open) {
      setPrefilled(false)
      return
    }
    if (prefilled) return

    if (isEdit && initialValues && address.provincesStatus === 'ready') {
      setForm({
        fullName: initialValues.fullName ?? '',
        email: initialValues.email ?? '',
        phoneNumber: initialValues.phoneNumber ?? '',
        staffRole: roleToLabel(initialValues.staffRole),
        streetNumber: initialValues.address?.streetNumber ?? '',
        streetName: initialValues.address?.streetName ?? '',
        employmentStatus: statusToLabel(initialValues.employmentStatus),
      })
      const province = address.provinces.find(
        (p) => p.provinceCode === initialValues.address?.provinceCode,
      )
      if (province) address.handleCityChange(province.provinceName)
      setPrefilled(true)
    } else if (!isEdit) {
      setForm(EMPTY_FORM)
      setPrefilled(true)
    }
    // address is a fresh object each render; prefilled guards this from looping.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isEdit, initialValues, prefilled, address.provincesStatus, address.provinces])
  /* eslint-enable react-hooks/set-state-in-effect */

  // Ward options only exist once the city's wards have loaded, so this waits for
  // that request rather than trying to prefill both levels at once.
  useEffect(() => {
    if (!isEdit || !prefilled || address.ward) return
    const wantedWard = initialValues?.address?.ward
    if (wantedWard && address.wards.some((w) => w.name === wantedWard)) {
      address.handleWardChange(wantedWard)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEdit, prefilled, address.wards, address.ward, initialValues])

  if (!open) return null

  const setField = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }))

  const handleSubmit = (event) => {
    event.preventDefault()
    const staffAddress = {
      streetNumber: form.streetNumber.trim(),
      streetName: form.streetName.trim(),
      ward: address.ward,
      provinceCode: address.cityCode,
    }

    onSubmit(
      isEdit
        ? {
            fullName: form.fullName.trim(),
            phoneNumber: form.phoneNumber.trim(),
            address: staffAddress,
            employmentStatus: statusToApi(form.employmentStatus),
          }
        : {
            fullName: form.fullName.trim(),
            email: form.email.trim(),
            phoneNumber: form.phoneNumber.trim(),
            staffRole: roleToApi(form.staffRole),
            address: staffAddress,
            branchProvinceCode: address.cityCode,
          },
    )
  }

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center overflow-y-auto bg-black/40 p-4">
      <div className="my-8 w-full max-w-[560px] rounded-xl border border-bk-border bg-bk-surface p-6 shadow-lg">
        <h2 className="mb-4 text-lg font-extrabold text-bk-text">
          {isEdit ? 'Edit staff member' : 'New staff account'}
        </h2>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
          {error && (
            <p className="rounded-[9px] border border-bk-red bg-bk-red-soft px-3 py-2 text-[13px] text-bk-red" role="alert">
              {error}
            </p>
          )}

          <div className="flex flex-col gap-1.5">
            <label className={LABEL_CLASS} htmlFor="staff-fullName">Full name</label>
            <input
              id="staff-fullName"
              type="text"
              value={form.fullName}
              onChange={setField('fullName')}
              required
              className={FIELD_CLASS}
            />
          </div>

          <div className="grid grid-cols-2 gap-3.5">
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-email">Email</label>
              <input
                id="staff-email"
                type="email"
                value={form.email}
                onChange={setField('email')}
                required
                disabled={isEdit}
                className={FIELD_CLASS}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-phone">Phone number</label>
              <input
                id="staff-phone"
                type="tel"
                value={form.phoneNumber}
                onChange={setField('phoneNumber')}
                required
                className={FIELD_CLASS}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3.5">
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-role">Role</label>
              {isEdit ? (
                <input id="staff-role" type="text" value={form.staffRole} disabled className={FIELD_CLASS} />
              ) : (
                <SelectField
                  id="staff-role"
                  value={form.staffRole}
                  onChange={(value) => setForm((prev) => ({ ...prev, staffRole: value }))}
                  options={ROLE_OPTIONS}
                />
              )}
            </div>
            {isEdit && (
              <div className="flex flex-col gap-1.5">
                <label className={LABEL_CLASS} htmlFor="staff-status">Status</label>
                <SelectField
                  id="staff-status"
                  value={form.employmentStatus}
                  onChange={(value) => setForm((prev) => ({ ...prev, employmentStatus: value }))}
                  options={STATUS_OPTIONS}
                />
              </div>
            )}
          </div>

          <div className="mt-1 text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">
            Address
          </div>

          <div className="grid grid-cols-2 gap-3.5">
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-streetNumber">Street number</label>
              <input
                id="staff-streetNumber"
                type="text"
                value={form.streetNumber}
                onChange={setField('streetNumber')}
                required
                className={FIELD_CLASS}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-streetName">Street name</label>
              <input
                id="staff-streetName"
                type="text"
                value={form.streetName}
                onChange={setField('streetName')}
                required
                className={FIELD_CLASS}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3.5">
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-city">City / Province</label>
              <SelectField
                id="staff-city"
                value={address.city}
                onChange={address.handleCityChange}
                options={address.provinces.map((p) => p.provinceName)}
                placeholder={address.cityPlaceholder}
                disabled={address.provincesStatus !== 'ready'}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASS} htmlFor="staff-ward">Ward</label>
              <SelectField
                id="staff-ward"
                value={address.ward}
                onChange={address.handleWardChange}
                options={address.wards.map((w) => w.name)}
                placeholder={address.wardPlaceholder}
                disabled={!address.city || address.wardsStatus !== 'ready'}
              />
            </div>
          </div>

          <div className="mt-2 flex justify-end gap-2.5">
            <button
              type="button"
              onClick={onCancel}
              disabled={pending}
              className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 text-[13.5px] font-bold text-bk-text hover:bg-bk-bg disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={pending}
              className="appearance-none rounded-[9px] border border-bk-teal bg-bk-teal px-4 py-2 text-[13.5px] font-bold text-white hover:bg-bk-teal-strong disabled:opacity-50"
            >
              {pending ? 'Saving…' : isEdit ? 'Save changes' : 'Create account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
