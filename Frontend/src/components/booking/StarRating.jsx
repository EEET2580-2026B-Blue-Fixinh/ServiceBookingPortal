import { MaterialIcon } from '../shared/icons'

export function StarRating({ value, max = 5 }) {
  return (
    <span className="inline-flex items-center gap-px" aria-label={`Rated ${value} out of ${max} stars`}>
      {Array.from({ length: max }, (_, index) => (
        <MaterialIcon
          key={index}
          name="star"
          filled={index < value}
          className={`text-base ${index < value ? 'text-amber-600' : 'text-bk-border'}`}
        />
      ))}
    </span>
  )
}
