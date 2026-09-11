import { useEffect, useState } from 'react'
import { fetchRatingCategories } from '../../../services/api/endpoints/referenceEndpoints'
import { fetchBookingRating, submitRating } from '../../../services/api/endpoints/ratingEndpoints'

// Req 3.4.M1 caps evidence at three images.
const MAX_EVIDENCE = 3

/**
 * Loads, shows and submits the customer's rating for one completed booking
 * (Reqs 3.4.S1-S3, 3.4.M1-M2).
 *
 * `hasRating` is a hint for the first render only: it decides whether there is
 * an existing rating worth asking for. From then on this hook's own requests are
 * the truth. A submit, a delete, or a 409 caused by a rating made elsewhere all
 * update `rating` directly rather than waiting for the booking to be read again.
 *
 * Each request keeps its own error because each failure has its own way out: a
 * failed lookup is retried from the card's loading state, a failed category list
 * from inside the form, and a failed submit is simply submitted again.
 */
export function useRating({ bookingRef, hasRating, onChanged }) {
  const [rating, setRating] = useState(null)
  // 'loading' until the existing rating is known, 'ready' once it is (or when
  // there was none to ask for), 'failed' when the lookup could not complete.
  const [loadStatus, setLoadStatus] = useState(hasRating ? 'loading' : 'ready')
  const [loadError, setLoadError] = useState(null)
  // Each bump asks for the existing rating again. Zero means there is nothing to
  // ask for, so an unrated booking makes no lookup at all.
  const [loadRequest, setLoadRequest] = useState(hasRating ? 1 : 0)

  const [categories, setCategories] = useState([])
  const [categoriesStatus, setCategoriesStatus] = useState('loading')
  const [categoriesError, setCategoriesError] = useState(null)
  const [categoriesRequest, setCategoriesRequest] = useState(0)

  const [overallScore, setOverallScore] = useState(0)
  const [scores, setScores] = useState({})
  const [comment, setComment] = useState('')
  const [evidence, setEvidence] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState(null)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (loadRequest === 0) return
    let cancelled = false
    fetchBookingRating(bookingRef)
      .then((existing) => {
        if (cancelled) return
        setRating(existing)
        setLoadError(null)
        setLoadStatus('ready')
      })
      .catch((requestError) => {
        if (cancelled) return
        // §8.2: no live rating is an ordinary outcome, not a fault. The flag that
        // sent us here can be stale after a delete elsewhere, so open the form.
        if (requestError.code === 'RATING_NOT_FOUND') {
          setRating(null)
          setLoadError(null)
          setLoadStatus('ready')
        } else {
          setLoadError(requestError)
          setLoadStatus('failed')
        }
      })
    return () => {
      cancelled = true
    }
  }, [bookingRef, loadRequest])

  useEffect(() => {
    let cancelled = false
    fetchRatingCategories()
      .then((items) => {
        if (cancelled) return
        setCategories(items)
        setCategoriesError(null)
        setCategoriesStatus('ready')
      })
      .catch((requestError) => {
        if (cancelled) return
        setCategoriesError(requestError)
        setCategoriesStatus('failed')
      })
    return () => {
      cancelled = true
    }
  }, [categoriesRequest])

  // Event handlers rather than effects, so both may show their loading state
  // straight away.
  const retryLoad = () => {
    setLoadStatus('loading')
    setLoadError(null)
    setLoadRequest((request) => request + 1)
  }

  const retryCategories = () => {
    setCategoriesStatus('loading')
    setCategoriesError(null)
    setCategoriesRequest((request) => request + 1)
  }

  const setCategoryScore = (categoryName, score) =>
    setScores((current) => ({ ...current, [categoryName]: score }))

  const chooseEvidence = (fileList) => setEvidence(Array.from(fileList).slice(0, MAX_EVIDENCE))

  const resetForm = () => {
    setOverallScore(0)
    setScores({})
    setComment('')
    setEvidence([])
    setSubmitError(null)
  }

  const submit = async () => {
    setSubmitting(true)
    setSubmitError(null)
    setNotice('')
    try {
      const completeScores =
        categories.length > 0 && categories.every(({ categoryName }) => scores[categoryName])
      const created = await submitRating({
        bookingRef,
        overallScore,
        reviewComment: comment,
        subcategoryScores: completeScores
          ? categories.map(({ categoryName }) => ({ categoryName, score: scores[categoryName] }))
          : [],
        evidence,
      })
      // §8.1 answers without canDelete. The author may always withdraw a rating
      // they have just made (§8.3), so it is set here rather than read back.
      setRating({ ...created, canDelete: true })
      onChanged?.()
    } catch (requestError) {
      // Rated from another tab or device since this page loaded. The form is out
      // of date rather than wrong, so show the rating that exists instead.
      if (requestError.code === 'RATING_ALREADY_EXISTS') {
        setNotice('This booking was already rated, so that rating is shown instead.')
        retryLoad()
      } else {
        setSubmitError(requestError)
      }
    } finally {
      setSubmitting(false)
    }
  }

  // Called by the delete hook once the rating is gone, however that was learnt.
  const markDeleted = () => {
    setRating(null)
    setNotice('')
    resetForm()
    onChanged?.()
  }

  return {
    rating,
    loadStatus,
    loadError,
    retryLoad,
    categories,
    categoriesStatus,
    categoriesError,
    retryCategories,
    overallScore,
    setOverallScore,
    scores,
    setCategoryScore,
    comment,
    setComment,
    evidence,
    chooseEvidence,
    // The sub-scores are part of the form, so it waits for their categories.
    canSubmit: overallScore > 0 && categoriesStatus === 'ready' && !submitting,
    submitting,
    submitError,
    submit,
    notice,
    markDeleted,
  }
}
