export const byName = (a, b) => a.fullName.localeCompare(b.fullName)

// Records matching firstValue on field sort before every other value; ties
// (both matching, or both not) fall back to tieBreak.
export function fieldFirst(field, firstValue, tieBreak = byName) {
  return (a, b) => {
    if (a[field] === b[field]) return tieBreak(a, b)
    return a[field] === firstValue ? -1 : 1
  }
}
