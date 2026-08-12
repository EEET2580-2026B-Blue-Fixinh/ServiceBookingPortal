import { useEffect, useRef, useState } from 'react'
import { MaterialIcon } from '../../icons'
import './SelectField.css'

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
  const rootRef = useRef(null)
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

  const openList = () => {
    setOpen(true)
    const selectedIndex = options.indexOf(value)
    setHighlighted(selectedIndex >= 0 ? selectedIndex : 0)
  }

  const commit = (index) => {
    if (options[index] != null) onChange(options[index])
    setOpen(false)
  }

  const jumpToLetter = (letter) => {
    clearTimeout(typeahead.current.timer)
    typeahead.current.text += letter.toLowerCase()
    const match = options.findIndex((option) =>
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
        setHighlighted((prev) => Math.min(prev + 1, options.length - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setHighlighted((prev) => Math.max(prev - 1, 0))
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        commit(highlighted)
        break
      case 'Escape':
        setOpen(false)
        break
      default:
        if (event.key.length === 1 && /[a-z]/i.test(event.key)) {
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
        <ul className="select-options" role="listbox">
          {options.map((option, index) => (
            <li
              key={option}
              role="option"
              aria-selected={option === value}
              className={`select-option${index === highlighted ? ' select-option-active' : ''}`}
              onMouseEnter={() => setHighlighted(index)}
              onMouseDown={(event) => {
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
  )
}
