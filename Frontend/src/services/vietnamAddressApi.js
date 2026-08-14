// Public, free, no-auth API for Vietnam's administrative divisions.
// https://provinces.open-api.vn
//
// Used for WARDS ONLY. The province list comes from our own backend
// (GET /reference/provinces), which is what defines the provinceCode registration
// sends and which regional store a customer belongs to — so the dropdown and the
// shard router can never disagree about which provinces exist.
//
// Pinned to v2 on purpose. The 2025 reform merged the 63 provinces into 34 and
// abolished the district tier, so a province contains wards directly. The
// unversioned /api/ path still redirects to v1's pre-merger data, which lists
// provinces that no longer exist — so always name the version.
//
// The path parameter is the official province code (mã tỉnh) that this API keys
// on, carried through our own province list as `officialCode`.
const BASE_URL = 'https://provinces.open-api.vn/api/v2'

async function request(path) {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) {
    throw new Error(`Vietnam address API request failed: ${response.status}`)
  }
  return response.json()
}

export function fetchProvinceWithWards(officialCode) {
  return request(`/p/${officialCode}?depth=2`)
}
