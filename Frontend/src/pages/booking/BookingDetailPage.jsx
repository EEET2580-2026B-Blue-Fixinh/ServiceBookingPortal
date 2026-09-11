import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { BookingShell } from "../../components/booking/BookingShell";
import { StatusBadge } from "../../components/booking/StatusBadge";
import { CancelBookingDialog } from "../../components/booking/CancelBookingDialog";
import {
  formatDate,
  formatDateTime,
  formatMoney,
} from "../../components/booking/bookingPresentation";
import { MaterialIcon } from "../../components/shared/icons";
import { Toast } from "../../components/shared/Toast";
import { RatingCard } from "../../components/booking/RatingCard";
import { ROUTES } from "../../config/constants";
import {
  cancelBooking,
  fetchBooking,
} from "../../services/api/endpoints/bookingEndpoints";

/** A label above its value — the shape every row on this page takes. */
function Field({ label, children }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[12px] font-semibold uppercase tracking-[0.05em] text-bk-muted">
        {label}
      </span>
      <span className="text-[14px] text-bk-text">{children}</span>
    </div>
  );
}

function Card({ title, children }) {
  return (
    <section className="mb-4 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
      {title && (
        <h2 className="mb-3.5 text-[15px] font-bold text-bk-text">{title}</h2>
      )}
      {children}
    </section>
  );
}

/**
 * Req 3.3.S6: service fee, material cost and total, for a completed booking.
 *
 * Section 7.3 omits `bill` entirely until the technician issues one and says in as many words
 * that its absence is not an error — so this renders a plain line rather than an empty table
 * with three dashes in it.
 */
function BillCard({ bill }) {
  if (!bill) {
    return (
      <Card title="Bill">
        <p className="text-[13.5px] text-bk-muted">
          No bill yet. It appears once the technician has completed the job and
          issued one.
        </p>
      </Card>
    );
  }

  return (
    <Card title="Bill">
      <dl className="flex flex-col gap-2.5">
        <div className="flex justify-between text-[14px] text-bk-text">
          <dt className="text-bk-muted">Service fee</dt>
          <dd>{formatMoney(bill.serviceFee)}</dd>
        </div>
        <div className="flex justify-between text-[14px] text-bk-text">
          <dt className="text-bk-muted">Material cost</dt>
          <dd>{formatMoney(bill.materialCost)}</dd>
        </div>
        {/* Total = fee + material (Req 3.3.S6). The server computes it so the two sides
            cannot disagree; this only shows what it computed. */}
        <div className="flex justify-between border-t border-bk-border pt-2.5 text-[15px] font-bold text-bk-text">
          <dt>Total</dt>
          <dd>{formatMoney(bill.totalCost)}</dd>
        </div>
      </dl>

      {bill.billImageUrl && (
        <a
          href={bill.billImageUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-3.5 inline-flex items-center gap-1.5 text-[13.5px] font-bold text-bk-teal hover:underline"
        >
          <MaterialIcon name="receipt_long" className="text-[19px]" />
          View the bill image
        </a>
      )}
      {bill.issuedAt && (
        <p className="mt-2 text-[12.5px] text-bk-muted">
          Issued {formatDateTime(bill.issuedAt)}
        </p>
      )}
    </Card>
  );
}

export function BookingDetailPage() {
  const { bookingRef } = useParams();
  const navigate = useNavigate();

  const [booking, setBooking] = useState(null);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState(null);
  const [reloadToken, setReloadToken] = useState(0);

  // `location.key` changes on every navigation, including one to the URL already
  // open. That is the case this reads: a customer sitting on this booking taps the
  // toast for it, React Router keeps the mounted page because the path has not
  // changed, and without this the screen would still show the state from before
  // the event that produced the notification.
  const location = useLocation();

  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelPending, setCancelPending] = useState(false);
  const [cancelError, setCancelError] = useState(null);
  const [cancelledMessage, setCancelledMessage] = useState(null);

  useEffect(() => {
    let cancelled = false;
    fetchBooking(bookingRef)
      .then((result) => {
        if (cancelled) return;
        setBooking(result);
        setError(null);
        setStatus("ready");
      })
      .catch((requestError) => {
        if (cancelled) return;
        setError(requestError);
        setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [bookingRef, reloadToken, location.key]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  const submitCancellation = async ({ reason, evidence }) => {
    setCancelPending(true);
    setCancelError(null);
    try {
      const result = await cancelBooking(bookingRef, { reason, evidence });
      setCancelOpen(false);
      setCancelledMessage(
        `This booking is now ${
          result.status === "TERMINATED_BY_CUSTOMER" ? "terminated" : "cancelled"
        }.`,
      );
      reload();
    } catch (requestError) {
      setCancelError(requestError);
    } finally {
      setCancelPending(false);
    }
  };

  // Whether this booking may still be cancelled. Section 7.4 refuses anything past with 409,
  // and Appendix A's four active values are the ones it accepts — the same grouping the list
  // screen gets from the server rather than deriving.
  const cancellable =
    booking != null &&
    ["CREATED", "CONFIRMED", "SCHEDULED", "WORKING"].includes(booking.status);

  return (
    <BookingShell
      activeNav="bookings"
      breadcrumb="Service Booking Portal"
      title={bookingRef}
      headerActions={
        <button
          type="button"
          onClick={() => navigate(ROUTES.BOOKING)}
          className="flex h-[38px] appearance-none items-center gap-1.5 rounded-[9px] border border-bk-border bg-bk-surface px-4 text-sm font-bold text-bk-text hover:bg-bk-bg"
        >
          <MaterialIcon name="arrow_back" className="text-[19px]" />
          My bookings
        </button>
      }
    >
      {cancelledMessage && (
        <Toast
          message={cancelledMessage}
          onDismiss={() => setCancelledMessage(null)}
        />
      )}
      {cancelOpen && booking && (
        <CancelBookingDialog
          booking={booking}
          pending={cancelPending}
          error={cancelError}
          onConfirm={submitCancellation}
          onClose={() => setCancelOpen(false)}
        />
      )}

      {status === "loading" && (
        <p className="font-mono text-[12.5px] text-bk-muted">Loading booking…</p>
      )}

      {status === "error" && (
        <div className="flex flex-col items-center gap-2.5 rounded-xl border border-dashed border-bk-red/40 px-5 py-14 text-center text-bk-muted">
          <MaterialIcon name="error" className="text-[32px] text-bk-red" />
          <p className="font-semibold text-bk-text">{error?.message}</p>
          <button
            type="button"
            onClick={() => navigate(ROUTES.BOOKING)}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 font-bold text-bk-teal hover:border-bk-teal hover:bg-bk-teal-soft"
          >
            Back to my bookings
          </button>
        </div>
      )}

      {status === "ready" && booking && (
        <>
          <Card>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-col gap-1">
                <span className="text-[17px] font-extrabold text-bk-text">
                  {booking.serviceName}
                </span>
                <span className="font-mono text-[12.5px] text-bk-muted">
                  {booking.bookingRef}
                </span>
              </div>
              <div className="flex items-center gap-3">
                <StatusBadge status={booking.status} />
                {cancellable && (
                  <button
                    type="button"
                    onClick={() => setCancelOpen(true)}
                    className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 text-[13.5px] font-bold text-bk-red hover:border-bk-red hover:bg-bk-red-soft"
                  >
                    Cancel booking
                  </button>
                )}
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4 max-[700px]:grid-cols-1">
              <Field label="Created">{formatDate(booking.createdAt)}</Field>
              <Field label="Preferred date">
                {formatDate(booking.preferredServiceDate)}
              </Field>
              <Field label="Available window">
                {booking.availableStartTime} – {booking.availableEndTime}
              </Field>
            </div>
          </Card>

          <Card title="The issue">
            <p className="whitespace-pre-wrap text-[14px] text-bk-text">
              {booking.issueDescription}
            </p>
          </Card>

          {/* Req 3.3.S5: a scheduled booking highlights when the technicians come, how long
              it should take, and who they are. Section 7.3 omits all three otherwise. */}
          {booking.scheduledStartAt && (
            <Card title="Scheduled visit">
              <div className="rounded-[10px] bg-bk-teal-soft p-4">
                <div className="mb-2 flex items-center gap-2.5 text-bk-teal-strong">
                  <MaterialIcon
                    name="event_available"
                    className="text-2xl text-bk-teal"
                  />
                  <span className="text-[15px] font-bold">
                    {formatDateTime(booking.scheduledStartAt)}
                  </span>
                </div>
                {booking.estimatedDurationMinutes && (
                  <p className="text-[13.5px] text-bk-teal-strong opacity-90">
                    Estimated duration {booking.estimatedDurationMinutes} minutes
                  </p>
                )}
                {booking.technicians?.length > 0 && (
                  <p className="mt-1 text-[13.5px] font-semibold text-bk-teal-strong">
                    {booking.technicians
                      .map((technician) => technician.fullName)
                      .join(", ")}
                  </p>
                )}
              </div>
            </Card>
          )}

          {booking.serviceAddress && (
            <Card title="Service address">
              <p className="text-[14px] text-bk-text">
                {[
                  booking.serviceAddress.streetNumber,
                  booking.serviceAddress.streetName,
                  booking.serviceAddress.ward,
                  // Section 7.3 publishes provinceName, resolved server-side from the code
                  // the booking stores.
                  booking.serviceAddress.provinceName,
                ]
                  .filter(Boolean)
                  .join(", ")}
              </p>
            </Card>
          )}

          <BillCard bill={booking.bill} />

          {booking.status === "COMPLETED" && (
            <RatingCard bookingRef={booking.bookingRef} hasRating={booking.hasRating} onChanged={reload} />
          )}

          {booking.cancellation && (
            <Card title="Cancellation">
              <p className="mb-3 whitespace-pre-wrap text-[14px] text-bk-text">
                {booking.cancellation.reason}
              </p>
              <p className="text-[12.5px] text-bk-muted">
                {formatDateTime(booking.cancellation.cancelledAt)}
              </p>
              {booking.cancellation.evidenceUrls?.length > 0 && (
                <ul className="mt-3 flex flex-wrap gap-2">
                  {booking.cancellation.evidenceUrls.map((url, index) => (
                    <li key={url}>
                      <a
                        href={url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-[9px] border border-bk-border bg-bk-bg px-3 py-2 text-[13px] font-bold text-bk-teal hover:border-bk-teal"
                      >
                        <MaterialIcon name="image" className="text-[18px]" />
                        Evidence {index + 1}
                      </a>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          )}

          <p className="font-mono text-[12px] text-bk-muted">
            {/* Section 2.8: reads are served from SBP's own copy, and lastSyncedAt is how
                current that copy is. Shown rather than implying the data is live. */}
            Last synced {formatDateTime(booking.lastSyncedAt)}
          </p>
        </>
      )}
    </BookingShell>
  );
}