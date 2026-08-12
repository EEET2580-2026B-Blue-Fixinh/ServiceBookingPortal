export function MaterialIcon({ name, filled = false, className = '', ...props }) {
  return (
    <span
      className={`material-symbols-rounded${filled ? ' icon-filled' : ''}${
        className ? ` ${className}` : ''
      }`}
      aria-hidden="true"
      {...props}
    >
      {name}
    </span>
  )
}
