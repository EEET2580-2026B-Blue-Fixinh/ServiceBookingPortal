const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const VN_PHONE_PATTERN = /^(?:\+84|0)(?:3|5|7|8|9)\d{8}$/

export const PASSWORD_REQUIREMENTS = [
  { key: 'length', label: '8+ characters', test: (value) => value.length >= 8 },
  { key: 'number', label: '1 number', test: (value) => /\d/.test(value) },
  { key: 'special', label: '1 special character', test: (value) => /[^A-Za-z0-9]/.test(value) },
  { key: 'capital', label: '1 capital letter', test: (value) => /[A-Z]/.test(value) },
]

export function isValidEmail(value) {
  return EMAIL_PATTERN.test(value.trim())
}

export function isValidVietnamesePhone(value) {
  return VN_PHONE_PATTERN.test(value.trim().replace(/[\s.-]/g, ''))
}

export function isValidLoginIdentifier(value) {
  return isValidEmail(value) || isValidVietnamesePhone(value)
}

export function isValidPassword(value) {
  return PASSWORD_REQUIREMENTS.every((requirement) => requirement.test(value))
}

export function doPasswordsMatch(password, confirmPassword) {
  return password.length > 0 && password === confirmPassword
}
