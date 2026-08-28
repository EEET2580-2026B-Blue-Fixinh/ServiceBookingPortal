import { useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { BookingShell } from "../../components/booking/BookingShell";
import { StatusBadge } from "../../components/booking/StatusBadge";
import { StarRating } from "../../components/booking/StarRating";
import { MaterialIcon } from "../../components/shared/icons";
import { SelectField } from "../../components/shared/form/SelectField/SelectField";
import { Toast } from "../../components/shared/Toast";
import {
  MOCK_BOOKINGS,
  SERVICE_ICON_BY_LABEL,
  SERVICE_TYPES,
} from "./mockBookings";

const SERVICE_OPTIONS = [
  "All services",
  ...SERVICE_TYPES.map((service) => service.label),
];
const SORT_OPTIONS = ["Newest first", "Oldest first"];

const INITIAL_FILTERS = {
  search: "",
  serviceType: "All services",
  createdFrom: "",
  createdTo: "",
  sort: "Newest first",
};

const DATE_INPUT_CLASS =
  "rounded-[9px] border border-bk-border bg-white px-3 py-2.5 font-[inherit] text-bk-text " +
  "focus:border-bk-teal focus:outline-none focus:ring-[3px] focus:ring-bk-teal-soft";

function matchesFilters(booking, filters) {
  const search = filters.search.trim().toLowerCase();
  if (search) {
    const haystack = [booking.id, ...booking.technicians]
      .join(" ")
      .toLowerCase();
    if (!haystack.includes(search)) return false;
  }

  if (
    filters.serviceType !== "All services" &&
    booking.serviceLabel !== filters.serviceType
  ) {
    return false;
  }

  if (filters.createdFrom && booking.createdDate < filters.createdFrom)
    return false;
  if (filters.createdTo && booking.createdDate > filters.createdTo)
    return false;

  return true;
}

function BookingCard({ booking }) {
  return (
    <article className="flex items-center gap-4 rounded-xl border border-bk-border bg-bk-surface px-[18px] py-4 max-[700px]:flex-wrap">
      <span className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-[10px] bg-bk-bg text-bk-text">
        <MaterialIcon
          name={SERVICE_ICON_BY_LABEL[booking.serviceLabel]}
          className="text-[21px]"
        />
      </span>

      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex items-baseline gap-2 text-[15.5px] font-bold text-bk-text">
          {booking.serviceLabel}
          <span className="font-mono text-[12.5px] font-medium text-bk-muted">
            {booking.id}
          </span>
        </span>
        <span className="truncate text-[13px] text-bk-muted">
          {booking.meta}
        </span>
      </span>

      {booking.schedule && (
        <span className="flex shrink-0 items-center gap-2.5 rounded-[10px] bg-bk-teal-soft px-[14px] py-[9px] text-bk-teal-strong">
          <MaterialIcon
            name="event_available"
            className="text-xl text-bk-teal"
          />
          <span className="flex flex-col leading-[1.3]">
            <span className="text-[13.5px] font-bold">
              {booking.schedule.label}
            </span>
            <span className="text-[12.5px] text-bk-teal-strong opacity-85">
              {booking.technicians.join(", ")}
            </span>
          </span>
        </span>
      )}

      <span className="ml-auto flex shrink-0 items-center gap-3.5 max-[700px]:ml-0 max-[700px]:w-full max-[700px]:justify-between max-[700px]:pl-[58px]">
        {booking.total && (
          <span className="flex flex-col items-end leading-[1.3]">
            <span className="text-[10.5px] font-bold uppercase tracking-[0.06em] text-bk-muted">
              Total
            </span>
            <span className="text-[14.5px] font-bold text-bk-text">
              {booking.total}
            </span>
          </span>
        )}
        <StatusBadge status={booking.status} />
        {booking.rating != null && <StarRating value={booking.rating} />}
        <MaterialIcon name="chevron_right" className="text-xl text-bk-muted" />
      </span>
    </article>
  );
}

function BookingSection({ title, dotClassName, count, bookings }) {
  if (bookings.length === 0) return null;

  return (
    <section className="mb-[26px]">
      <h2 className="mb-3 flex items-center gap-2 text-[15px] font-bold text-bk-text">
        <span className={`h-2 w-2 rounded-full ${dotClassName}`} />
        {title}
        <span className="font-semibold text-bk-muted">{count}</span>
      </h2>
      <div className="flex flex-col gap-2.5">
        {bookings.map((booking) => (
          <BookingCard key={booking.id} booking={booking} />
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
  const [showWelcome, setShowWelcome] = useState(
    Boolean(location.state?.justSignedIn),
  );

  const setFilter = (key) => (value) =>
    setFilters((prev) => ({ ...prev, [key]: value }));

  const filtered = useMemo(() => {
    const matched = MOCK_BOOKINGS.filter((booking) =>
      matchesFilters(booking, filters),
    );
    const sorted = [...matched].sort((a, b) =>
      filters.sort === "Newest first"
        ? b.createdDate.localeCompare(a.createdDate)
        : a.createdDate.localeCompare(b.createdDate),
    );
    return {
      active: sorted.filter((booking) => booking.group === "active"),
      past: sorted.filter((booking) => booking.group === "past"),
    };
  }, [filters]);

  const totalCount = filtered.active.length + filtered.past.length;
  const hasResults = totalCount > 0;

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
      {location.state?.googleAccountLinked && (
        <p
          className="mb-4 rounded-lg bg-bk-teal-soft px-4 py-3 text-sm font-semibold text-bk-teal-strong"
          role="status"
        >
          Your Google account is now linked to your Fixinh account.
        </p>
      )}
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
            value={filters.serviceType}
            onChange={setFilter("serviceType")}
            options={SERVICE_OPTIONS}
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
            options={SORT_OPTIONS}
          />
        </div>
      </div>

      <p className="mb-[18px] font-mono text-[12.5px] text-bk-muted">
        {totalCount} of {MOCK_BOOKINGS.length} bookings
      </p>

      {hasResults ? (
        <>
          <BookingSection
            title="Active bookings"
            dotClassName="bg-bk-teal"
            count={filtered.active.length}
            bookings={filtered.active}
          />
          <BookingSection
            title="Past bookings"
            dotClassName="bg-bk-muted"
            count={filtered.past.length}
            bookings={filtered.past}
          />
        </>
      ) : (
        <div className="flex flex-col items-center gap-2.5 rounded-xl border border-dashed border-bk-border px-5 py-14 text-center text-bk-muted">
          <MaterialIcon name="search_off" className="text-[32px]" />
          <p>No bookings match your filters.</p>
          <button
            type="button"
            onClick={() => setFilters(INITIAL_FILTERS)}
            className="appearance-none rounded-[9px] border border-bk-border bg-bk-surface px-4 py-2 font-bold text-bk-teal hover:border-bk-teal hover:bg-bk-teal-soft"
          >
            Clear filters
          </button>
        </div>
      )}
    </BookingShell>
  );
}
