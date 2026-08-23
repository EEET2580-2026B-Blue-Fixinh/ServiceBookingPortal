const VND_FORMATTER = new Intl.NumberFormat('vi-VN')

// Vietnamese dong, no decimals (API Documentation Section 2.6): 500000 -> "500.000 ₫".
export function formatVnd(amount) {
  return `${VND_FORMATTER.format(amount)} ₫`
}

// Minutes as a short label for a service card: 120 -> "2h", 90 -> "1h 30m", 45 -> "45m".
export function formatDurationMinutes(minutes) {
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  if (hours === 0) return `${remainder}m`
  if (remainder === 0) return `${hours}h`
  return `${hours}h ${remainder}m`
}
