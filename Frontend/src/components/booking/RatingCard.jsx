import { StarRating } from './StarRating'
import { useDeleteRating } from './hooks/useDeleteRating'
import { useRating } from './hooks/useRating'

const RETRY_BUTTON =
  'rounded-sm text-[13px] font-bold text-bk-teal hover:underline focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-bk-teal-soft'

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

/** A request that could not complete: what failed, why, and the way to ask again. */
function RequestFailure({ title, error, onRetry }) {
  return (
    <div role="alert" className="space-y-1">
      <p className="text-[13px] font-semibold text-bk-red">{title}</p>
      <p className="text-[13px] text-bk-muted">{error.message}</p>
      <button type="button" onClick={onRetry} className={RETRY_BUTTON}>
        Try again
      </button>
    </div>
  )
}

function ErrorLine({ error }) {
  if (!error) return null
  return (
    <p role="alert" className="text-[13px] font-semibold text-bk-red">
      {error.message}
    </p>
  )
}

export function RatingCard({ bookingRef, hasRating, onChanged }) {
  // Route parameter changes reuse the page; each booking needs its own form state.
  return (
    <BookingRatingCard
      key={`${bookingRef}:${Boolean(hasRating)}`}
      bookingRef={bookingRef}
      hasRating={hasRating}
      onChanged={onChanged}
    />
  )
}

function BookingRatingCard({ bookingRef, hasRating, onChanged }) {
  const card = useRating({ bookingRef, hasRating, onChanged })
  const deletion = useDeleteRating({ rating: card.rating, onDeleted: card.markDeleted })

  const handleSubmit = (event) => {
    event.preventDefault()
    card.submit()
  }

  return (
    <section className="mb-4 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
      <h2 className="mb-3.5 text-[15px] font-bold text-bk-text">Your rating</h2>

      {card.notice && (
        <p role="status" className="mb-3 text-[13px] text-bk-muted">
          {card.notice}
        </p>
      )}

      {card.loadStatus === 'loading' ? (
        <p className="text-[13px] text-bk-muted">Loading your rating…</p>
      ) : card.loadStatus === 'failed' ? (
        <RequestFailure title="Couldn't load your rating." error={card.loadError} onRetry={card.retryLoad} />
      ) : card.rating ? (
        <div className="space-y-3">
          <StarRating value={card.rating.overallScore} />
          {card.rating.reviewComment && (
            <p className="whitespace-pre-wrap text-[14px] text-bk-text">{card.rating.reviewComment}</p>
          )}
          {card.rating.subcategoryScores?.map((item) => (
            <div key={item.categoryName} className="flex items-center justify-between">
              <span className="text-[13px] text-bk-muted">{item.categoryName}</span>
              <StarRating value={item.score} />
            </div>
          ))}
          {card.rating.canDelete && (
            <div className="space-y-2">
              <button
                type="button"
                disabled={deletion.deleting}
                onClick={deletion.remove}
                className="rounded-[9px] border border-bk-red px-3 py-2 text-[13px] font-bold text-bk-red disabled:opacity-50"
              >
                {deletion.deleting ? 'Deleting…' : 'Delete rating'}
              </button>
              <ErrorLine error={deletion.deleteError} />
            </div>
          )}
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <ScorePicker label="Overall score" value={card.overallScore} onChange={card.setOverallScore} />

          {card.categoriesStatus === 'loading' && (
            <p className="text-[13px] text-bk-muted">Loading rating categories…</p>
          )}
          {card.categoriesStatus === 'failed' && (
            <RequestFailure
              title="Couldn't load the rating categories."
              error={card.categoriesError}
              onRetry={card.retryCategories}
            />
          )}
          {card.categoriesStatus === 'ready' && (
            <div className="grid grid-cols-3 gap-3 max-[700px]:grid-cols-1">
              {card.categories.map(({ categoryName }) => (
                <ScorePicker
                  key={categoryName}
                  label={categoryName}
                  value={card.scores[categoryName] || 0}
                  onChange={(score) => card.setCategoryScore(categoryName, score)}
                />
              ))}
            </div>
          )}

          <textarea
            value={card.comment}
            maxLength={1000}
            onChange={(event) => card.setComment(event.target.value)}
            placeholder="Share your experience (optional)"
            className="min-h-24 w-full rounded-[9px] border border-bk-border bg-bk-bg p-3 text-[14px] text-bk-text"
          />
          <input
            type="file"
            accept="image/jpeg,image/png"
            multiple
            onChange={(event) => card.chooseEvidence(event.target.files)}
            className="block text-[13px] text-bk-muted"
          />

          <ErrorLine error={card.submitError} />
          <button
            type="submit"
            disabled={!card.canSubmit}
            className="rounded-[9px] bg-bk-teal px-4 py-2 text-[13.5px] font-bold text-white disabled:opacity-50"
          >
            {card.submitting ? 'Submitting…' : 'Submit rating'}
          </button>
        </form>
      )}
    </section>
  )
}
