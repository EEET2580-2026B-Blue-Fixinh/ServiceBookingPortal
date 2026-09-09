import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookingShell } from '../../components/booking/BookingShell'
import { StarRating } from '../../components/booking/StarRating'
import { MaterialIcon } from '../../components/shared/icons'
import { ROUTES } from '../../config/constants'
import { fetchMyRatings } from '../../services/api/endpoints/ratingEndpoints'

function RatingItem({ rating }) {
  return (
    <article className="rounded-xl border border-bk-border bg-bk-surface p-[18px] shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to={ROUTES.bookingDetail(rating.bookingRef)} className="font-mono text-[13px] font-bold text-bk-teal hover:underline">
            {rating.bookingRef}
          </Link>
          <div className="mt-2"><StarRating value={rating.overallScore} /></div>
        </div>
        <time className="text-[12.5px] text-bk-muted">
          {new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(rating.submittedAt))}
        </time>
      </div>

      {rating.reviewComment && <p className="mt-4 whitespace-pre-wrap text-[14px] text-bk-text">{rating.reviewComment}</p>}

      {rating.subcategoryScores?.length > 0 && (
        <div className="mt-4 grid grid-cols-3 gap-3 max-[700px]:grid-cols-1">
          {rating.subcategoryScores.map((score) => (
            <div key={score.categoryName} className="rounded-[9px] bg-bk-bg p-3">
              <p className="mb-1 text-[12px] font-semibold text-bk-muted">{score.categoryName}</p>
              <StarRating value={score.score} />
            </div>
          ))}
        </div>
      )}

      {rating.evidenceUrls?.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {rating.evidenceUrls.map((url, index) => (
            <a key={url} href={url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 rounded-[8px] border border-bk-border px-3 py-2 text-[12.5px] font-bold text-bk-teal">
              <MaterialIcon name="image" className="text-[17px]" /> Image {index + 1}
            </a>
          ))}
        </div>
      )}
    </article>
  )
}

export function MyRatingsPage() {
  const [ratings, setRatings] = useState([])
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchMyRatings().then((items) => { setRatings(items); setStatus('ready') })
      .catch((requestError) => { setError(requestError); setStatus('error') })
  }, [])

  return (
    <BookingShell activeNav="ratings" breadcrumb="Service Booking Portal" title="My ratings">
      {status === 'loading' && <p className="font-mono text-[12.5px] text-bk-muted">Loading ratings…</p>}
      {status === 'error' && <p className="rounded-xl border border-bk-red/30 bg-bk-surface p-5 font-semibold text-bk-red">{error?.message}</p>}
      {status === 'ready' && ratings.length === 0 && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-bk-border bg-bk-surface px-5 py-16 text-center">
          <MaterialIcon name="reviews" className="text-[36px] text-bk-muted" />
          <p className="font-bold text-bk-text">You have not submitted a rating yet.</p>
          <Link to={ROUTES.BOOKING} className="text-[13.5px] font-bold text-bk-teal hover:underline">View completed bookings</Link>
        </div>
      )}
      {status === 'ready' && ratings.length > 0 && <div className="grid gap-4">{ratings.map((rating) => <RatingItem key={rating.ratingId} rating={rating} />)}</div>}
    </BookingShell>
  )
}
