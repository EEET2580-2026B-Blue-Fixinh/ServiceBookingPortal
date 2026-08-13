import { useEffect, useRef } from 'react'
import { MaterialIcon } from '../shared/icons'
import './AuthLayout.css'

const MOBILE_QUERY = '(max-width: 900px)'
const INTRO_DELAY_MS = 1200
const INTRO_DURATION_MS = 1400
const SNAP_DURATION_MS = 450
const SNAP_SETTLE_MS = 150
// How close to a section boundary (as a fraction of viewport height) counts
// as "close enough" to nudge the rest of the way once scrolling settles.
const SNAP_PROXIMITY_RATIO = 0.25
// Raw input events, not 'scroll' — animateWindowScrollTo() calls window.scrollTo(),
// which also fires 'scroll', so listening for that would cancel the intro the
// instant it starts moving.
const USER_INTENT_EVENTS = ['wheel', 'touchstart', 'keydown']

function easeInOutCubic(t) {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2
}

function animateWindowScrollTo(targetTop, duration, onDone) {
  const startTop = window.scrollY
  const distance = targetTop - startTop
  const startTime = performance.now()

  function step(now) {
    const progress = Math.min((now - startTime) / duration, 1)
    window.scrollTo(0, startTop + distance * easeInOutCubic(progress))
    if (progress < 1) {
      requestAnimationFrame(step)
    } else {
      onDone?.()
    }
  }

  requestAnimationFrame(step)
}

function BrandMark() {
  return (
    <span className="auth-logo">
      <span className="auth-logo-mark">
        <MaterialIcon name="handyman" className="auth-logo-icon" />
      </span>
      Fixinh
    </span>
  )
}

export function AuthLayout({ brand, children }) {
  const panelRef = useRef(null)

  // One-time guided intro: after a beat, glide from the brand section down to
  // the form. Cancelled the moment the user shows scroll/touch/key intent, so
  // it never fights someone who's already reading or interacting.
  useEffect(() => {
    const removeIntentListeners = () => {
      USER_INTENT_EVENTS.forEach((eventName) => window.removeEventListener(eventName, cancelIntro))
    }

    const cancelIntro = () => {
      clearTimeout(timer)
      removeIntentListeners()
    }

    const timer = setTimeout(() => {
      removeIntentListeners()

      // Checked here, not at effect setup, so resizing the window (or toggling
      // devtools responsive mode) after the page already loaded still works —
      // an early return at mount time would miss that.
      if (!window.matchMedia(MOBILE_QUERY).matches) return

      const panel = panelRef.current
      if (!panel) return
      const targetTop = panel.getBoundingClientRect().top + window.scrollY

      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        window.scrollTo(0, targetTop)
      } else {
        animateWindowScrollTo(targetTop, INTRO_DURATION_MS)
      }
    }, INTRO_DELAY_MS)

    USER_INTENT_EVENTS.forEach((eventName) =>
      window.addEventListener(eventName, cancelIntro, { passive: true }),
    )

    return cancelIntro
  }, [])

  // Repeatable section snapping: scrolling itself stays completely native —
  // nothing is intercepted mid-gesture. Only once scroll motion settles do we
  // check whether the page is close to the brand/form boundary and, if so,
  // ease the rest of the way. Works in both directions, every time. Scrolling
  // deep inside a section (e.g. partway through the long Register form) is
  // nowhere near a boundary, so it's left completely alone.
  useEffect(() => {
    let settleTimer
    let isSnapping = false

    const checkSnap = () => {
      if (isSnapping) return
      if (!window.matchMedia(MOBILE_QUERY).matches) return
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

      const panel = panelRef.current
      if (!panel) return

      const panelTop = panel.getBoundingClientRect().top + window.scrollY
      const threshold = window.innerHeight * SNAP_PROXIMITY_RATIO
      const current = window.scrollY

      let target = null
      if (Math.abs(current - 0) <= threshold) {
        target = 0
      } else if (Math.abs(current - panelTop) <= threshold) {
        target = panelTop
      }

      if (target === null || Math.abs(current - target) < 1) return

      isSnapping = true
      animateWindowScrollTo(target, SNAP_DURATION_MS, () => {
        isSnapping = false
      })
    }

    const handleScroll = () => {
      if (isSnapping) return
      clearTimeout(settleTimer)
      settleTimer = setTimeout(checkSnap, SNAP_SETTLE_MS)
    }

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', handleScroll)
      clearTimeout(settleTimer)
    }
  }, [])

  return (
    <div className="auth-split">
      <aside className="auth-brand">
        <div className="auth-brand-header">
          <BrandMark />
        </div>
        <div className="auth-brand-body">{brand}</div>
        <div className="auth-brand-footer">
          <MaterialIcon name="lock" className="auth-footer-icon" />
          Service Booking Portal &middot; TLS 1.3
        </div>
      </aside>

      <main className="auth-panel" ref={panelRef}>
        <div className="auth-panel-body">{children}</div>
      </main>
    </div>
  )
}
