import { useEffect, useState } from 'react'
import { StarRating } from './StarRating'
import { fetchRatingCategories } from '../../services/api/endpoints/referenceEndpoints'
import { deleteRating, fetchBookingRating, submitRating } from '../../services/api/endpoints/ratingEndpoints'

function ScorePicker({ value, onChange, label }) {
  return (
    <div>
      <p className="mb-1 text-[13px] font-semibold text-bk-text">{label}</p>
      <div className="flex gap-1" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((score) => (
          <button key={score} type="button" role="radio" aria-checked={value === score}
            onClick={() => onChange(score)} className={`text-2xl ${score <= value ? 'text-amber-600' : 'text-bk-border'}`}>
            ★
          </button>
        ))}
      </div>
    </div>
  )
}

export function RatingCard({ bookingRef, hasRating, onChanged }) {
  // Route parameter changes reuse the page; each booking needs its own form state.
  return <BookingRatingCard key={`${bookingRef}:${Boolean(hasRating)}`} bookingRef={bookingRef} hasRating={hasRating} onChanged={onChanged} />
}

function BookingRatingCard({ bookingRef, hasRating, onChanged }) {
  const [rating, setRating] = useState(null)
  const [ratingLoaded, setRatingLoaded] = useState(!hasRating)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [categories, setCategories] = useState([])
  const [overallScore, setOverallScore] = useState(0)
  const [scores, setScores] = useState({})
  const [comment, setComment] = useState('')
  const [evidence, setEvidence] = useState([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    fetchRatingCategories().then((items) => {
      if (!cancelled) setCategories(items)
    }).catch((requestError) => {
      if (!cancelled) setError(requestError)
    })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (!hasRating) return
    let cancelled = false
    fetchBookingRating(bookingRef).then((existing) => {
      if (cancelled) return
      setRating(existing)
      setRatingLoaded(true)
      setError(null)
    }).catch((requestError) => {
      if (cancelled) return
      // The list flag can be stale after a rating was deleted in another tab.
      if (requestError.code === 'RATING_NOT_FOUND') {
        setRating(null)
        setRatingLoaded(true)
        setError(null)
      } else {
        setError(requestError)
      }
    })
    return () => { cancelled = true }
  }, [bookingRef, hasRating, loadAttempt])

  const submit = async (event) => {
    event.preventDefault()
    setPending(true); setError(null)
    try {
      const completeScores = categories.length > 0 && categories.every(({ categoryName }) => scores[categoryName])
      const created = await submitRating({ bookingRef, overallScore, reviewComment: comment,
        subcategoryScores: completeScores ? categories.map(({ categoryName }) => ({ categoryName, score: scores[categoryName] })) : [], evidence })
      setRating({ ...created, canDelete: true }); onChanged?.()
    } catch (requestError) { setError(requestError) } finally { setPending(false) }
  }

  const remove = async () => {
    setPending(true); setError(null)
    try { await deleteRating(rating.ratingId); setRating(null); setOverallScore(0); setScores({}); setComment(''); setEvidence([]); onChanged?.() }
    catch (requestError) { setError(requestError) } finally { setPending(false) }
  }

  return (
    <section className="mb-4 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
      <h2 className="mb-3.5 text-[15px] font-bold text-bk-text">Your rating</h2>
      {error && <p className="mb-3 text-[13px] font-semibold text-bk-red">{error.message}</p>}
      {!ratingLoaded ? (
        <div>
          <p className="text-[13px] text-bk-muted">Loading your rating…</p>
          {error && <button type="button" onClick={() => { setError(null); setLoadAttempt((attempt) => attempt + 1) }} className="mt-2 text-[13px] font-bold text-bk-teal">Try again</button>}
        </div>
      ) : rating ? (
        <div className="space-y-3">
          <StarRating value={rating.overallScore} />
          {rating.reviewComment && <p className="whitespace-pre-wrap text-[14px] text-bk-text">{rating.reviewComment}</p>}
          {rating.subcategoryScores?.map((item) => <div key={item.categoryName} className="flex items-center justify-between"><span className="text-[13px] text-bk-muted">{item.categoryName}</span><StarRating value={item.score} /></div>)}
          {rating.canDelete && <button type="button" disabled={pending} onClick={remove} className="rounded-[9px] border border-bk-red px-3 py-2 text-[13px] font-bold text-bk-red disabled:opacity-50">Delete rating</button>}
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <ScorePicker label="Overall score" value={overallScore} onChange={setOverallScore} />
          <div className="grid grid-cols-3 gap-3 max-[700px]:grid-cols-1">
            {categories.map(({ categoryName }) => <ScorePicker key={categoryName} label={categoryName} value={scores[categoryName] || 0} onChange={(value) => setScores((current) => ({ ...current, [categoryName]: value }))} />)}
          </div>
          <textarea value={comment} maxLength={1000} onChange={(event) => setComment(event.target.value)} placeholder="Share your experience (optional)" className="min-h-24 w-full rounded-[9px] border border-bk-border bg-bk-bg p-3 text-[14px] text-bk-text" />
          <input type="file" accept="image/jpeg,image/png" multiple onChange={(event) => setEvidence(Array.from(event.target.files).slice(0, 3))} className="block text-[13px] text-bk-muted" />
          <button type="submit" disabled={pending || overallScore === 0} className="rounded-[9px] bg-bk-teal px-4 py-2 text-[13.5px] font-bold text-white disabled:opacity-50">{pending ? 'Submitting…' : 'Submit rating'}</button>
        </form>
      )}
    </section>
  )
}
