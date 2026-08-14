import { useEffect, useMemo, useRef, useState } from 'react'
import { MaterialIcon } from '../../icons'
import './SelectField.css'

// Above this many options, scrolling stops being a reasonable way to find one
// and the list gets a search box instead. Ho Chi Minh City alone has 168 wards.
const SEARCHABLE_THRESHOLD = 8

// "Phường Thủ Dầu Một" should be found by typing "thu dau mot" — few people
// reach for diacritics mid-form, and every ward name starts with the same
// couple of words, so a plain prefix match would be useless here.
function normalise(value) {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
}

export function SelectField({
  id,
  value,
  onChange,
  options,
  placeholder = 'Select an option',
  disabled = false,
}) {
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(-1)
  const [query, setQuery] = useState('')
  const rootRef = useRef(null)
  const searchRef = useRef(null)
  const typeahead = useRef({ text: '', timer: null })

  useEffect(() => {
    function handleClickOutside(event) {
      if (rootRef.current && !rootRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Disabling mid-interaction shouldn't need an effect to force-close the
  // list — just stop treating it as open while disabled is true.
  const isOpen = open && !disabled
  const searchable = options.length > SEARCHABLE_THRESHOLD

  const visibleOptions = useMemo(() => {
    if (!searchable || !query.trim()) return options
    const needle = normalise(query.trim())
    return options.filter((option) => normalise(option).includes(needle))
  }, [options, query, searchable])

  useEffect(() => {
    if (isOpen && searchable) searchRef.current?.focus()
  }, [isOpen, searchable])

  const openList = () => {
    setOpen(true)
    setQuery('')
    const selectedIndex = options.indexOf(value)
    setHighlighted(selectedIndex >= 0 ? selectedIndex : 0)
  }

  // Indexes are into visibleOptions, which shrinks as the query narrows.
  const commit = (index) => {
    if (visibleOptions[index] != null) onChange(visibleOptions[index])
    setOpen(false)
    setQuery('')
  }

  const jumpToLetter = (letter) => {
    clearTimeout(typeahead.current.timer)
    typeahead.current.text += letter.toLowerCase()
    const match = visibleOptions.findIndex((option) =>
      option.toLowerCase().startsWith(typeahead.current.text),
    )
    if (match >= 0) setHighlighted(match)
    typeahead.current.timer = setTimeout(() => {
      typeahead.current.text = ''
    }, 500)
  }

  const handleKeyDown = (event) => {
    if (!isOpen) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault()
        openList()
      }
      return
    }

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setHighlighted((prev) => Math.min(prev + 1, visibleOptions.length - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setHighlighted((prev) => Math.max(prev - 1, 0))
        break
      case 'Enter':
        event.preventDefault()
        commit(highlighted)
        break
      case ' ':
        // A space is a character while searching, not a "pick this one".
        if (!searchable) {
          event.preventDefault()
          commit(highlighted)
        }
        break
      case 'Escape':
        setOpen(false)
        break
      default:
        // The search box does its own matching; typeahead is for lists without one.
        if (!searchable && event.key.length === 1 && /[a-z]/i.test(event.key)) {
          jumpToLetter(event.key)
        }
    }
  }

  return (
    <div className="select-field" ref={rootRef}>
      <button
        type="button"
        id={id}
        className="select-trigger"
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        disabled={disabled}
        onClick={() => (isOpen ? setOpen(false) : openList())}
        onKeyDown={handleKeyDown}
      >
        <span className={`select-value${value ? '' : ' select-placeholder'}`}>
          {value || placeholder}
        </span>
        <MaterialIcon
          name="expand_more"
          className={`select-chevron${isOpen ? ' select-chevron-open' : ''}`}
        />
      </button>

      {isOpen && (
        <div className="select-popover">
          {searchable && (
            <input
              ref={searchRef}
              type="text"
              className="select-search"
              placeholder="Search…"
              value={query}
              aria-label="Search options"
              onChange={(event) => {
                setQuery(event.target.value)
                setHighlighted(0)
              }}
              onKeyDown={handleKeyDown}
            />
          )}

          {visibleOptions.length === 0 ? (
            <p className="select-empty">No match for “{query}”</p>
          ) : (
            <ul className="select-options" role="listbox">
              {visibleOptions.map((option, index) => (
                <li
                  key={option}
                  role="option"
                  aria-selected={option === value}
                  className={`select-option${index === highlighted ? ' select-option-active' : ''}`}
                  onMouseEnter={() => setHighlighted(index)}
                  onMouseDown={(event) => {
                    // Keeps focus in the search box so the click does not blur it first.
                    event.preventDefault()
                    commit(index)
                  }}
                >
                  {option}
                  {option === value && <MaterialIcon name="check" className="select-check" />}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
