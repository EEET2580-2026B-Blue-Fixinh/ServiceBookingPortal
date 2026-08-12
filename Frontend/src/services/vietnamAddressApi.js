// Public, free, no-auth API for Vietnam's administrative divisions.
// https://provinces.open-api.vn — currently serves the pre-2025-merger
// 63-province structure (province -> district -> ward).
const BASE_URL = 'https://provinces.open-api.vn/api'

async function request(path) {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) {
    throw new Error(`Vietnam address API request failed: ${response.status}`)
  }
  return response.json()
}

export function fetchProvinces() {
  return request('/?depth=1')
}

export function fetchProvinceWithDistrictsAndWards(provinceCode) {
  return request(`/p/${provinceCode}?depth=3`)
}
