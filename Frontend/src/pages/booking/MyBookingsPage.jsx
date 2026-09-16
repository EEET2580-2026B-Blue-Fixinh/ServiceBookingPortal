import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { BookingShell } from "../../components/booking/BookingShell";
import { StatusBadge } from "../../components/booking/StatusBadge";
import {
  buildMeta,
  buildSchedule,
  formatMoney,
  iconResolver,
} from "../../components/booking/bookingPresentation";
import { CancelBookingDialog } from "../../components/booking/CancelBookingDialog";
import { MaterialIcon } from "../../components/shared/icons";
import { Pagination } from "../../components/shared/Pagination";
import { SelectField } from "../../components/shared/form/SelectField/SelectField";
import { Toast } from "../../components/shared/Toast";
import { ROUTES } from "../../config/constants";
import { useMyBookings } from "../../hooks/useMyBookings";
import { cancelBooking } from "../../services/api/endpoints/bookingEndpoints";
import { fetchRepairServices } from "../../services/api/endpoints/referenceEndpoints";

const ALL_SERVICES = "All services";

// Section 7.2 accepts ACTIVE or PAST and nothing finer. The issue asked for tabs "by
// status", but the API groups rather than filters by each of Appendix A's eight values —
// and grouping is what Req 3.3.M2 actually asks the screen to show.
const GROUP_TABS = [
  { label: "All", value: undefined },
  { label: "Active", value: "ACTIVE" },
  { label: "Past", value: "PAST" },
];

// Section 7.2 accepts exactly these two. The label is what the customer picks; the value
// is what goes on the wire, so no part of the UI has to know the sort syntax.
const SORT_OPTIONS = [
  { label: "Newest first", value: "createdAt,desc" },
  { label: "Oldest first", value: "createdAt,asc" },
];

const INITIAL_FILTERS = {
  search: "",
  serviceName: ALL_SERVICES,
  createdFrom: "",
  createdTo: "",
  sort: SORT_OPTIONS[0].label,
};

// Long enough that a typed word is one request, short enough to feel immediate.
const SEARCH_DEBOUNCE_MS = 350;

// Sent as `size` on every request. The server slices the page before splitting it into
// active and past, so this caps both sections together, not each one.
const BOOKINGS_PAGE_SIZE = 5;

const DATE_INPUT_CLASS =
  "rounded-[9px] border border-bk-border bg-white px-3 py-2.5 font-[inherit] text-bk-text " +
  "focus:border-bk-teal focus:outline-none focus:ring-[3px] focus:ring-bk-teal-soft";

function BookingCard({ booking, iconFor, onCancel, onOpen }) {
  const schedule = buildSchedule(booking);
  const total = formatMoney(booking.totalCost);

  return (
    // The row opens the booking; the Cancel button inside it does not. Anything interactive
    // nested here stops the click before it reaches this handler.
    <article
      role="link"
      tabIndex={0}
      onClick={() => onOpen(booking)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onOpen(booking);
        }
      }}
      className="flex cursor-pointer items-center gap-4 rounded-xl border border-bk-border bg-bk-surface px-[18px] py-4 hover:border-bk-teal focus:border-bk-teal focus:outline-none focus:ring-[3px] focus:ring-bk-teal-soft flex-wrap"
    >
      <span className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-[10px] bg-bk-bg text-bk-text">
        <MaterialIcon
          name={iconFor(booking.serviceId)}
          className="text-[21px]"
        />
      </span>

      {/* Wraps by content, not by a viewport width (design mockup, booking row): the name
          block and the schedule chip each declare the width they need, and the row breaks
          wherever the two stop fitting side by side. A breakpoint could only guess at
          that point, and guessed wrong on small laptops with the sidebar open. */}
      <span className="flex min-w-0 flex-1 basis-[180px] flex-col gap-1">
        <span className="flex flex-wrap items-baseline gap-2 text-[15.5px] font-bold text-bk-text">
          {booking.serviceName}
          <span className="font-mono text-[12.5px] font-medium text-bk-muted">
            {booking.bookingRef}
          </span>
        </span>
        <span className="truncate text-[13px] text-bk-muted">
          {buildMeta(booking)}
        </span>
      </span>

      {schedule && (
        <span className="flex flex-1 basis-[210px] items-center gap-2.5 rounded-[10px] bg-bk-teal-soft px-[14px] py-[9px] text-bk-teal-strong">
          <MaterialIcon
            name="event_available"
            className="text-xl text-bk-teal"
          />
          <span className="flex min-w-0 flex-col leading-[1.3]">
            <span className="text-[13.5px] font-bold">{schedule.label}</span>
            {schedule.technicians.length > 0 && (
              <span className="truncate text-[12.5px] text-bk-teal-strong opacity-85">
                {schedule.technicians.join(", ")}
              </span>
            )}
          </span>
        </span>
      )}

      <span className="ml-auto flex shrink-0 items-center gap-3.5">
        {total && (
          <span className="flex flex-col items-end leading-[1.3]">
            <span className="text-[10.5px] font-bold uppercase tracking-[0.06em] text-bk-muted">
              Total
            </span>
            <span className="text-[14.5px] font-bold text-bk-text">{total}</span>
          </span>
        )}
        <StatusBadge status={booking.status} />
        {/* Section 7.2 publishes hasRating, a boolean — not the score. Stars would need a
            second request per row, which is the cost hasRating exists to avoid. The rating
            screens are not built yet, so this stays a flag until they are. */}
        {booking.hasRating && (
          <MaterialIcon
            name="star"
            filled
            className="text-xl text-bk-amber"
            title="You have rated this booking"
          />
        )}
        {/* Only active bookings get the control, and "active" is the array the server put
            this row in — not a status list re-derived here. Section 7.4 refuses anything
            past with 409, so a second opinion could only ever disagree with the first. */}
        {onCancel && (
          <button
            type="button"
            onClick={(event) => {
              // Without this the row's own handler fires too and the customer lands on the
              // detail page with a dialog they did not ask for.
              event.stopPropagation();
              onCancel(booking);
            }}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-3 py-1.5 text-[13px] font-bold text-bk-red hover:border-bk-red hover:bg-bk-red-soft"
          >
            Cancel
          </button>
        )}
        <MaterialIcon name="chevron_right" className="text-xl text-bk-muted" />
      </span>
    </article>
  );
}

function BookingSection({
  title,
  dotClassName,
  bookings,
  iconFor,
  onCancel,
  onOpen,
}) {
  if (bookings.length === 0) return null;

  return (
    <section className="mb-[26px]">
      <h2 className="mb-3 flex items-center gap-2 text-[15px] font-bold text-bk-text">
        <span className={`h-2 w-2 rounded-full ${dotClassName}`} />
        {title}
        <span className="font-semibold text-bk-muted">{bookings.length}</span>
      </h2>
      <div className="flex flex-col gap-2.5">
        {bookings.map((booking) => (
          <BookingCard
            key={booking.bookingRef}
            booking={booking}
            iconFor={iconFor}
            onCancel={onCancel}
            onOpen={onOpen}
          />
        ))}
      </div>
    </section>
  );
}

export function MyBookingsPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [filters, setFilters] = useState(INITIAL_FILTERS);
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [group, setGroup] = useState(undefined);
  const [page, setPage] = useState(0);
  const [services, setServices] = useState([]);
  const [showWelcome, setShowWelcome] = useState(
    Boolean(location.state?.justSignedIn),
  );

  // The booking the dialog is open for, and how that request is going. Held here rather
  // than inside the dialog because the page is what submits, what refreshes the list
  // afterwards, and what has to survive the dialog closing.
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelPending, setCancelPending] = useState(false);
  const [cancelError, setCancelError] = useState(null);
  const [cancelledMessage, setCancelledMessage] = useState(null);

  const setFilter = (key) => (value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
    // Any change to what is being asked for invalidates which page we were on: page 3 of
    // the old result set is not page 3 of the new one.
    setPage(0);
  };

  // The filter dropdown asks for inactive services too (Section 5.2's own note), so a
  // booking made before a service was retired can still be filtered for by name.
  useEffect(() => {
    let cancelled = false;
    fetchRepairServices({ activeOnly: false })
      .then((items) => {
        if (!cancelled) setServices(items);
      })
      .catch(() => {
        // A failed catalogue costs the dropdown its options, not the page its bookings.
        if (!cancelled) setServices([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const timer = setTimeout(
      () => setDebouncedSearch(filters.search),
      SEARCH_DEBOUNCE_MS,
    );
    return () => clearTimeout(timer);
  }, [filters.search]);

  const serviceIdsByName = useMemo(() => {
    const byName = new Map();
    services.forEach((service) => {
      const existing = byName.get(service.serviceName) ?? [];
      byName.set(service.serviceName, [...existing, service.serviceId]);
    });
    return byName;
  }, [services]);

  const query = useMemo(
    () => ({
      q: debouncedSearch,
      serviceIds: serviceIdsByName.get(filters.serviceName),
      createdFrom: filters.createdFrom,
      createdTo: filters.createdTo,
      group,
      sort: SORT_OPTIONS.find((option) => option.label === filters.sort)?.value,
      page,
      size: BOOKINGS_PAGE_SIZE,
    }),
    [
      debouncedSearch,
      serviceIdsByName,
      filters.serviceName,
      filters.createdFrom,
      filters.createdTo,
      filters.sort,
      group,
      page,
    ],
  );

  const { active, past, page: pageInfo, status, error, reload } =
    useMyBookings(query);

  const openBooking = (booking) =>
    navigate(ROUTES.bookingDetail(booking.bookingRef));

  const openCancelDialog = (booking) => {
    setCancelTarget(booking);
    setCancelError(null);
  };

  const submitCancellation = async ({ reason, evidence }) => {
    setCancelPending(true);
    setCancelError(null);
    try {
      // The response names the status the server chose — CANCELLED_BY_CUSTOMER or
      // TERMINATED_BY_CUSTOMER — so the message tells the customer what actually
      // happened instead of assuming which one it was.
      const result = await cancelBooking(cancelTarget.bookingRef, {
        reason,
        evidence,
      });
      setCancelTarget(null);
      setCancelledMessage(
        `${result.bookingRef} is now ${
          result.status === "TERMINATED_BY_CUSTOMER" ? "terminated" : "cancelled"
        }.`,
      );
      // Refetched rather than patched in place: the booking has to move from the active
      // array to the past one, and the server is what decides which array it belongs in.
      reload();
    } catch (requestError) {
      // The dialog stays open holding what was typed, so a rejected reason can be fixed
      // rather than retyped.
      setCancelError(requestError);
    } finally {
      setCancelPending(false);
    }
  };

  const iconFor = useMemo(() => iconResolver(services), [services]);

  const serviceOptions = useMemo(
    () => [
      ALL_SERVICES,
      ...[...new Set(services.map((service) => service.serviceName))].sort(),
    ],
    [services],
  );

  const totalElements = pageInfo?.totalElements ?? 0;
  const totalPages = pageInfo?.totalPages ?? 0;
  const hasResults = active.length > 0 || past.length > 0;

  // Cancelling the last booking on the final page of the Active tab moves it out of this
  // result set, leaving the page empty. Step back to the new last page instead of showing
  // "No bookings match your filters" while matches still exist. Adjusted during render
  // rather than in an effect, so the empty page never paints.
  if (status === "ready" && page > 0 && page >= totalPages) {
    setPage(Math.max(totalPages - 1, 0));
  }

  return (
    <BookingShell
      activeNav="bookings"
      breadcrumb="Service Booking Portal"
      title="My bookings"
      headerActions={
        <button
          type="button"
          onClick={() => navigate("/booking/new")}
          className="flex h-[38px] appearance-none items-center gap-1.5 rounded-[9px] bg-bk-teal px-4 text-sm font-bold text-white hover:bg-bk-teal-strong"
        >
          <MaterialIcon name="add" className="text-[19px]" />
          New booking
        </button>
      }
    >
      {showWelcome && (
        <Toast
          message={`Welcome back${user?.fullName ? `, ${user.fullName}` : ""}!`}
          onDismiss={() => setShowWelcome(false)}
        />
      )}
      {cancelledMessage && (
        <Toast
          message={cancelledMessage}
          onDismiss={() => setCancelledMessage(null)}
        />
      )}
      {cancelTarget && (
        <CancelBookingDialog
          booking={cancelTarget}
          pending={cancelPending}
          error={cancelError}
          onConfirm={submitCancellation}
          onClose={() => setCancelTarget(null)}
        />
      )}
      {location.state?.googleAccountLinked && (
        <p
          className="mb-4 rounded-lg bg-bk-teal-soft px-4 py-3 text-sm font-semibold text-bk-teal-strong"
          role="status"
        >
          Your Google account is now linked to your Fixinh account.
        </p>
      )}

      <div
        className="mb-3.5 flex gap-1.5 rounded-[10px] border border-bk-border bg-bk-surface p-1.5"
        role="tablist"
        aria-label="Filter bookings by group"
      >
        {GROUP_TABS.map((tab) => {
          const selected = group === tab.value;
          return (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => {
                setGroup(tab.value);
                setPage(0);
              }}
              className={`appearance-none rounded-[7px] px-4 py-1.5 text-[13.5px] font-bold ${
                selected
                  ? "bg-bk-teal text-white"
                  : "bg-transparent text-bk-muted hover:bg-bk-bg hover:text-bk-text"
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <div className="mb-[18px] grid grid-cols-[minmax(220px,1.6fr)_repeat(4,minmax(140px,1fr))] gap-3.5 rounded-xl border border-bk-border bg-bk-surface p-[18px] max-[1100px]:grid-cols-2 max-[700px]:grid-cols-1">
        <div className="flex min-w-0 flex-col gap-1.5">
          <label
            htmlFor="bk-search"
            className="text-[12.5px] font-semibold text-bk-muted"
          >
            Search
          </label>
          <div className="flex items-center gap-2 rounded-[9px] border border-bk-border bg-white px-3 focus-within:border-bk-teal focus-within:ring-[3px] focus-within:ring-bk-teal-soft">
            <MaterialIcon
              name="search"
              className="shrink-0 text-[19px] text-bk-muted"
            />
            <input
              id="bk-search"
              type="text"
              placeholder="Booking ID or technician name"
              value={filters.search}
              onChange={(event) => setFilter("search")(event.target.value)}
              className="min-w-0 flex-1 bg-transparent py-2.5 font-[inherit] text-bk-text focus:outline-none"
            />
          </div>
        </div>

        <div className="flex min-w-0 flex-col gap-1.5">
          <label
            htmlFor="bk-service-type"
            className="text-[12.5px] font-semibold text-bk-muted"
          >
            Service type
          </label>
          <SelectField
            id="bk-service-type"
            value={filters.serviceName}
            onChange={setFilter("serviceName")}
            options={serviceOptions}
          />
        </div>

        <div className="flex min-w-0 flex-col gap-1.5">
          <label
            htmlFor="bk-created-from"
            className="text-[12.5px] font-semibold text-bk-muted"
          >
            Created from
          </label>
          <input
            id="bk-created-from"
            type="date"
            value={filters.createdFrom}
            onChange={(event) => setFilter("createdFrom")(event.target.value)}
            className={DATE_INPUT_CLASS}
          />
        </div>

        <div className="flex min-w-0 flex-col gap-1.5">
          <label
            htmlFor="bk-created-to"
            className="text-[12.5px] font-semibold text-bk-muted"
          >
            Created to
          </label>
          <input
            id="bk-created-to"
            type="date"
            value={filters.createdTo}
            onChange={(event) => setFilter("createdTo")(event.target.value)}
            className={DATE_INPUT_CLASS}
          />
        </div>

        <div className="flex min-w-0 flex-col gap-1.5">
          <label
            htmlFor="bk-sort"
            className="text-[12.5px] font-semibold text-bk-muted"
          >
            Sort by created
          </label>
          <SelectField
            id="bk-sort"
            value={filters.sort}
            onChange={setFilter("sort")}
            options={SORT_OPTIONS.map((option) => option.label)}
          />
        </div>
      </div>

      {status === "error" ? (
        <div className="flex flex-col items-center gap-2.5 rounded-xl border border-dashed border-bk-red/40 px-5 py-14 text-center text-bk-muted">
          <MaterialIcon name="error" className="text-[32px] text-bk-red" />
          {/* Section 2.7: the backend writes the message, so the UI shows it rather than
              inventing wording that would drift from the real rule. */}
          <p className="font-semibold text-bk-text">{error?.message}</p>
          <button
            type="button"
            onClick={reload}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 font-bold text-bk-teal hover:border-bk-teal hover:bg-bk-teal-soft"
          >
            Try again
          </button>
        </div>
      ) : (
        <>
          <p className="mb-[18px] font-mono text-[12.5px] text-bk-muted">
            {status === "loading"
              ? "Loading bookings…"
              : `${totalElements} booking${totalElements === 1 ? "" : "s"}`}
          </p>

          {hasResults ? (
            <>
              {/* Rendered in the order the server gave them. Section 7.2: active above
                  past, createdAt within each — the frontend does not re-sort. */}
              <BookingSection
                title="Active bookings"
                dotClassName="bg-bk-teal"
                bookings={active}
                iconFor={iconFor}
                onCancel={openCancelDialog}
                onOpen={openBooking}
              />
              <BookingSection
                title="Past bookings"
                dotClassName="bg-bk-muted"
                bookings={past}
                iconFor={iconFor}
                onOpen={openBooking}
              />

              {/* The shared control counts pages from 1; the API counts from 0. */}
              <Pagination
                page={page + 1}
                pageSize={BOOKINGS_PAGE_SIZE}
                totalItems={totalElements}
                itemNoun="bookings"
                onPageChange={(nextPage) => setPage(nextPage - 1)}
              />
            </>
          ) : (
            status === "ready" && (
              <div className="flex flex-col items-center gap-2.5 rounded-xl border border-dashed border-bk-border px-5 py-14 text-center text-bk-muted">
                <MaterialIcon name="search_off" className="text-[32px]" />
                <p>No bookings match your filters.</p>
                <button
                  type="button"
                  onClick={() => {
                    setFilters(INITIAL_FILTERS);
                    setPage(0);
                  }}
                  className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 font-bold text-bk-teal hover:border-bk-teal hover:bg-bk-teal-soft"
                >
                  Clear filters
                </button>
              </div>
            )
          )}
        </>
      )}
    </BookingShell>
  );
}
