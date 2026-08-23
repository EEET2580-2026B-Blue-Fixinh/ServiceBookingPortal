import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BookingShell } from '../../components/booking/BookingShell'
import { ServiceCard } from '../../components/booking/ServiceCard'
import { MaterialIcon } from '../../components/shared/icons'
import { SelectField } from '../../components/shared/form/SelectField/SelectField'
import { useVietnamAddress } from '../../hooks/useVietnamAddress'
import { fetchRepairServices } from '../../services/api/endpoints/referenceEndpoints'
import { createBooking } from '../../services/api/endpoints/bookingEndpoints'
import { formatDurationMinutes, formatVnd } from '../../utils/format'
import { ROUTES } from '../../config/constants'

const ALL_CATEGORIES = 'All categories'

const INITIAL_FORM = {
  issueDescription: '',
  preferredServiceDate: '',
  availableStartTime: '',
  availableEndTime: '',
  streetNumber: '',
  streetName: '',
}

// Which form field a backend fieldErrors[] entry belongs to (API Documentation Section 2.7),
// same idea as RegisterPage's map. `serviceId` is deliberately absent: that violation means the
// picked service vanished mid-flow, so it is handled by bouncing back to the browse step
// (see the SERVICE_NOT_FOUND branch in handleSubmit) rather than by highlighting a field that
// is not on this screen.
const FIELD_BY_BACKEND_NAME = {
  issueDescription: 'issueDescription',
  preferredServiceDate: 'preferredServiceDate',
  availableStartTime: 'availableStartTime',
  availableEndTime: 'availableEndTime',
  serviceAddress: 'city',
  'serviceAddress.provinceCode': 'city',
}

// Section 7.1's error table, for the codes that are not a per-field violation.
const ERROR_CODE_MESSAGES = {
  PREFERRED_DATE_IN_PAST: 'The preferred date must be today or later.',
  TIME_WINDOW_INVALID: 'End time must be at least 60 minutes after the start time.',
  SMP_UNAVAILABLE: "We couldn't reach the service system. Please try again in a moment.",
}

// A handful of "CREATED"-shaped labels — every booking from this form starts here
// (Req 3.3.S2), so nothing more elaborate than a lookup is needed yet.
const STATUS_LABELS = { CREATED: 'Created' }

function todayIsoDate() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function isWindowValid(start, end) {
  const [startH, startM] = start.split(':').map(Number)
  const [endH, endM] = end.split(':').map(Number)
  return endH * 60 + endM - (startH * 60 + startM) >= 60
}

function inputClass(hasError) {
  return (
    'rounded-[9px] border bg-white px-3 py-2.5 font-[inherit] text-bk-text focus:outline-none focus:ring-[3px] ' +
    (hasError
      ? 'border-bk-red focus:ring-bk-red-soft'
      : 'border-bk-border focus:border-bk-teal focus:ring-bk-teal-soft')
  )
}

function FormField({ label, htmlFor, error, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-[12.5px] font-semibold text-bk-muted">
        {label}
      </label>
      {children}
      {error && <p className="text-[12.5px] font-semibold text-bk-red">{error}</p>}
    </div>
  )
}

function ErrorBanner({ message }) {
  if (!message) return null
  return (
    <p className="mb-4 rounded-lg bg-bk-red-soft px-4 py-3 text-sm font-semibold text-bk-red" role="alert">
      {message}
    </p>
  )
}

function EmptyState({ icon, children }) {
  return (
    <div className="flex flex-col items-center gap-2.5 rounded-xl border border-dashed border-bk-border px-5 py-14 text-center text-bk-muted">
      <MaterialIcon name={icon} className="text-[32px]" />
      {children}
    </div>
  )
}

function ServiceBrowseStep({
  services,
  status,
  categories,
  categoryFilter,
  onCategoryChange,
  onSelect,
  onRetry,
  formError,
}) {
  return (
    <>
      <ErrorBanner message={formError} />

      <div className="mb-[18px] flex flex-wrap items-end gap-3.5 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
        <div className="flex min-w-[220px] flex-col gap-1.5">
          <label htmlFor="nb-category" className="text-[12.5px] font-semibold text-bk-muted">
            Category
          </label>
          <SelectField
            id="nb-category"
            value={categoryFilter}
            onChange={onCategoryChange}
            options={categories}
            disabled={status !== 'ready'}
          />
        </div>
        {status === 'ready' && (
          <p className="ml-auto pb-2.5 font-mono text-[12.5px] text-bk-muted">
            {services.length} service{services.length === 1 ? '' : 's'}
          </p>
        )}
      </div>

      {status === 'loading' && <EmptyState icon="progress_activity"><p>Loading services…</p></EmptyState>}

      {status === 'error' && (
        <EmptyState icon="wifi_off">
          <p>Couldn't load services.</p>
          <button
            type="button"
            onClick={onRetry}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 font-bold text-bk-teal hover:border-bk-teal hover:bg-bk-teal-soft"
          >
            Retry
          </button>
        </EmptyState>
      )}

      {status === 'ready' && services.length === 0 && (
        <EmptyState icon="search_off"><p>No services in this category.</p></EmptyState>
      )}

      {status === 'ready' && services.length > 0 && (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-3.5">
          {services.map((service) => (
            <ServiceCard key={service.serviceId} service={service} onSelect={onSelect} />
          ))}
        </div>
      )}
    </>
  )
}

function BookingFormStep({
  service,
  form,
  errors,
  formError,
  submitting,
  address,
  onChange,
  onBack,
  onSubmit,
  minDate,
}) {
  return (
    <div className="mx-auto max-w-[640px]">
      <button
        type="button"
        onClick={onBack}
        className="mb-4 flex appearance-none items-center gap-1.5 bg-transparent p-0 text-[13.5px] font-bold text-bk-muted hover:text-bk-teal"
      >
        <MaterialIcon name="arrow_back" className="text-lg" />
        Change service
      </button>

      <div className="mb-5 flex items-center gap-3.5 rounded-xl border border-bk-border bg-bk-surface p-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[10px] bg-bk-teal-soft text-bk-teal-strong">
          <MaterialIcon name="handyman" className="text-2xl" />
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="truncate text-[15.5px] font-bold text-bk-text">{service.serviceName}</span>
          <span className="text-[13px] text-bk-muted">
            {formatVnd(service.baseServiceFee)} · {formatDurationMinutes(service.standardDurationMinutes)}
          </span>
        </span>
      </div>

      <ErrorBanner message={formError} />

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <FormField label="Describe the issue" htmlFor="nb-issue" error={errors.issueDescription}>
          <textarea
            id="nb-issue"
            name="issueDescription"
            rows={4}
            placeholder="What's wrong? (10–1000 characters)"
            value={form.issueDescription}
            onChange={onChange}
            aria-invalid={Boolean(errors.issueDescription)}
            className={inputClass(errors.issueDescription)}
          />
        </FormField>

        <div className="grid grid-cols-3 gap-3.5 max-[560px]:grid-cols-1">
          <FormField label="Preferred date" htmlFor="nb-date" error={errors.preferredServiceDate}>
            <input
              id="nb-date"
              type="date"
              name="preferredServiceDate"
              min={minDate}
              value={form.preferredServiceDate}
              onChange={onChange}
              className={inputClass(errors.preferredServiceDate)}
            />
          </FormField>
          <FormField label="Available from" htmlFor="nb-start" error={errors.availableStartTime}>
            <input
              id="nb-start"
              type="time"
              name="availableStartTime"
              value={form.availableStartTime}
              onChange={onChange}
              className={inputClass(errors.availableStartTime)}
            />
          </FormField>
          <FormField label="Available to" htmlFor="nb-end" error={errors.availableEndTime}>
            <input
              id="nb-end"
              type="time"
              name="availableEndTime"
              value={form.availableEndTime}
              onChange={onChange}
              className={inputClass(errors.availableEndTime)}
            />
          </FormField>
        </div>

        <div className="text-[12.5px] font-bold uppercase tracking-[0.04em] text-bk-muted">
          Service address
        </div>

        <div className="grid grid-cols-2 gap-3.5 max-[560px]:grid-cols-1">
          <FormField label="Street number" htmlFor="nb-street-number" error={errors.streetNumber}>
            <input
              id="nb-street-number"
              type="text"
              name="streetNumber"
              value={form.streetNumber}
              onChange={onChange}
              className={inputClass(errors.streetNumber)}
            />
          </FormField>
          <FormField label="Street name" htmlFor="nb-street-name" error={errors.streetName}>
            <input
              id="nb-street-name"
              type="text"
              name="streetName"
              value={form.streetName}
              onChange={onChange}
              className={inputClass(errors.streetName)}
            />
          </FormField>
        </div>

        <div className="grid grid-cols-2 gap-3.5 max-[560px]:grid-cols-1">
          <FormField label="City / Province" htmlFor="nb-city" error={errors.city}>
            <SelectField
              id="nb-city"
              value={address.city}
              onChange={address.handleCityChange}
              options={address.provinces.map((province) => province.provinceName)}
              placeholder={address.cityPlaceholder}
              disabled={address.provincesStatus !== 'ready'}
            />
          </FormField>
          <FormField label="Ward" htmlFor="nb-ward" error={errors.ward}>
            <SelectField
              id="nb-ward"
              value={address.ward}
              onChange={address.handleWardChange}
              options={address.wards.map((ward) => ward.name)}
              placeholder={address.wardPlaceholder}
              disabled={!address.city || address.wardsStatus !== 'ready'}
            />
          </FormField>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="mt-2 flex h-[42px] appearance-none items-center justify-center rounded-[9px] bg-bk-teal font-bold text-white hover:bg-bk-teal-strong disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting ? 'Submitting…' : 'Confirm booking'}
        </button>
      </form>
    </div>
  )
}

function ConfirmationStep({ booking, onViewBookings, onBookAnother }) {
  return (
    <div className="mx-auto flex max-w-[520px] flex-col items-center gap-4 rounded-xl border border-bk-border bg-bk-surface px-8 py-12 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-full bg-bk-green-soft text-bk-green">
        <MaterialIcon name="check_circle" filled className="text-[32px]" />
      </span>
      <h2 className="text-xl font-extrabold text-bk-text">Booking confirmed</h2>
      <p className="text-bk-muted">{booking.message}</p>

      <div className="flex w-full flex-col gap-2 rounded-[10px] bg-bk-bg p-4 text-left">
        <div className="flex items-center justify-between gap-3">
          <span className="text-[12.5px] font-semibold text-bk-muted">Booking reference</span>
          <span className="font-mono text-[13.5px] font-bold text-bk-text">{booking.bookingRef}</span>
        </div>
        <div className="flex items-center justify-between gap-3">
          <span className="text-[12.5px] font-semibold text-bk-muted">Service</span>
          <span className="truncate text-[13.5px] font-bold text-bk-text">{booking.serviceName}</span>
        </div>
        <div className="flex items-center justify-between gap-3">
          <span className="text-[12.5px] font-semibold text-bk-muted">Preferred date</span>
          <span className="text-[13.5px] font-bold text-bk-text">{booking.preferredServiceDate}</span>
        </div>
        <div className="flex items-center justify-between gap-3">
          <span className="text-[12.5px] font-semibold text-bk-muted">Status</span>
          <span className="inline-flex items-center rounded-full bg-bk-teal-soft px-3 py-1 text-[12.5px] font-bold text-bk-teal-strong">
            {STATUS_LABELS[booking.status] ?? booking.status}
          </span>
        </div>
      </div>

      <div className="mt-2 flex w-full gap-3">
        <button
          type="button"
          onClick={onBookAnother}
          className="flex h-[42px] flex-1 appearance-none items-center justify-center rounded-[9px] border border-bk-border bg-bk-surface font-bold text-bk-text hover:border-bk-teal hover:bg-bk-teal-soft"
        >
          Book another
        </button>
        <button
          type="button"
          onClick={onViewBookings}
          className="flex h-[42px] flex-1 appearance-none items-center justify-center rounded-[9px] bg-bk-teal font-bold text-white hover:bg-bk-teal-strong"
        >
          View my bookings
        </button>
      </div>
    </div>
  )
}

export function NewBookingPage() {
  const navigate = useNavigate()
  const address = useVietnamAddress()

  const [step, setStep] = useState('browse') // 'browse' | 'form' | 'confirmation'
  const [services, setServices] = useState([])
  const [servicesStatus, setServicesStatus] = useState('loading')
  const [categoryFilter, setCategoryFilter] = useState(ALL_CATEGORIES)
  const [selectedService, setSelectedService] = useState(null)
  const [form, setForm] = useState(INITIAL_FORM)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [confirmation, setConfirmation] = useState(null)

  // Mirrors useVietnamAddress's own loadProvinces/retryProvinces split: this function
  // sets state only inside the promise callback, never synchronously, so it is safe to
  // call directly from the mount effect below. retryServices is the version that resets
  // to "loading" first — used from event handlers (a click, a failed submit), never
  // from an effect body.
  const loadServices = useCallback(() => {
    fetchRepairServices()
      .then((items) => {
        setServices(items)
        setServicesStatus('ready')
      })
      .catch(() => setServicesStatus('error'))
  }, [])

  useEffect(() => {
    loadServices()
  }, [loadServices])

  const retryServices = () => {
    setServicesStatus('loading')
    loadServices()
  }

  const categories = useMemo(() => {
    const names = [...new Set(services.map((service) => service.categoryName))]
    return [ALL_CATEGORIES, ...names]
  }, [services])

  const visibleServices = useMemo(() => {
    if (categoryFilter === ALL_CATEGORIES) return services
    return services.filter((service) => service.categoryName === categoryFilter)
  }, [services, categoryFilter])

  const handleSelectService = (service) => {
    setSelectedService(service)
    setErrors({})
    setFormError('')
    setStep('form')
  }

  const handleFormChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
    setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
    setFormError('')
  }

  const handleBookAnother = () => {
    setForm(INITIAL_FORM)
    setSelectedService(null)
    setConfirmation(null)
    setErrors({})
    setFormError('')
    setStep('browse')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    const nextErrors = {}
    const description = form.issueDescription.trim()
    if (description.length < 10 || description.length > 1000) {
      nextErrors.issueDescription = 'Describe the issue in 10 to 1000 characters.'
    }
    if (!form.preferredServiceDate) {
      nextErrors.preferredServiceDate = 'Select a preferred date.'
    } else if (form.preferredServiceDate < todayIsoDate()) {
      nextErrors.preferredServiceDate = 'The preferred date must be today or later.'
    }
    if (!form.availableStartTime) {
      nextErrors.availableStartTime = 'Select a start time.'
    }
    if (!form.availableEndTime) {
      nextErrors.availableEndTime = 'Select an end time.'
    } else if (
      form.availableStartTime &&
      !isWindowValid(form.availableStartTime, form.availableEndTime)
    ) {
      nextErrors.availableEndTime = 'Must be at least 60 minutes after the start time.'
    }
    if (!form.streetNumber.trim()) {
      nextErrors.streetNumber = 'Enter the street number.'
    }
    if (!form.streetName.trim()) {
      nextErrors.streetName = 'Enter the street name.'
    }
    if (!address.city) {
      nextErrors.city = 'Select a city or province.'
    }
    if (!address.ward) {
      nextErrors.ward = 'Select a ward.'
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    setFormError('')
    try {
      const booking = await createBooking({
        serviceId: selectedService.serviceId,
        issueDescription: description,
        preferredServiceDate: form.preferredServiceDate,
        availableStartTime: form.availableStartTime,
        availableEndTime: form.availableEndTime,
        serviceAddress: {
          streetNumber: form.streetNumber.trim(),
          streetName: form.streetName.trim(),
          ward: address.ward,
          provinceCode: address.cityCode,
        },
      })
      setConfirmation(booking)
      setStep('confirmation')
    } catch (error) {
      // The service picked a moment ago no longer exists (retired, or a stale list) —
      // there is no field on this screen to blame, so send the customer back to a
      // freshly reloaded browse step instead of showing a dead-end error.
      if (error.code === 'SERVICE_NOT_FOUND') {
        setStep('browse')
        setSelectedService(null)
        setFormError('This service is no longer available. Please pick another one.')
        retryServices()
        return
      }

      const backendFieldErrors = error.fieldErrorsByField?.() ?? {}
      const mapped = {}
      for (const [backendField, message] of Object.entries(backendFieldErrors)) {
        const formField = FIELD_BY_BACKEND_NAME[backendField]
        if (formField) mapped[formField] = message
      }
      setErrors(mapped)

      if (Object.keys(mapped).length === 0) {
        setFormError(ERROR_CODE_MESSAGES[error.code] ?? error.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <BookingShell
      activeNav="new"
      breadcrumb="Service Booking Portal"
      title={step === 'confirmation' ? 'Booking confirmed' : 'New booking'}
    >
      {step === 'browse' && (
        <ServiceBrowseStep
          services={visibleServices}
          status={servicesStatus}
          categories={categories}
          categoryFilter={categoryFilter}
          onCategoryChange={setCategoryFilter}
          onSelect={handleSelectService}
          onRetry={retryServices}
          formError={formError}
        />
      )}

      {step === 'form' && selectedService && (
        <BookingFormStep
          service={selectedService}
          form={form}
          errors={errors}
          formError={formError}
          submitting={submitting}
          address={address}
          onChange={handleFormChange}
          onBack={() => setStep('browse')}
          onSubmit={handleSubmit}
          minDate={todayIsoDate()}
        />
      )}

      {step === 'confirmation' && confirmation && (
        <ConfirmationStep
          booking={confirmation}
          onViewBookings={() => navigate(ROUTES.BOOKING)}
          onBookAnother={handleBookAnother}
        />
      )}
    </BookingShell>
  )
}
