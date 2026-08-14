import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchProvinces } from '../services/api/endpoints/referenceEndpoints'
import { fetchProvinceWithWards } from '../services/vietnamAddressApi'

// Owns the cascading city/ward selection for Vietnam addresses.
//
// Two levels, not three — the district tier was abolished in the 2025 reform, so
// wards hang directly off a province.
//
// The two levels come from different places on purpose: provinces from our own
// backend, because it defines the provinceCode registration sends and which
// regional store a customer lands in; wards from the public address API, which
// the backend does not mirror. The province's `officialCode` is what joins them.
export function useVietnamAddress() {
  const [city, setCity] = useState('')
  const [ward, setWard] = useState('')

  const [provinces, setProvinces] = useState([])
  const [provincesStatus, setProvincesStatus] = useState('loading')

  const [wards, setWards] = useState([])
  const [wardsStatus, setWardsStatus] = useState('idle')

  const provinceByName = useMemo(
    () => Object.fromEntries(provinces.map((province) => [province.provinceName, province])),
    [provinces],
  )

  // What the API stores as address.provinceCode — e.g. "HCM". The name is for
  // display only; it is the code that is sent.
  const cityCode = useMemo(
    () => (city ? (provinceByName[city]?.provinceCode ?? '') : ''),
    [city, provinceByName],
  )

  // Only setState inside the promise callback here, never synchronously in the
  // effect body — retry flows reset the status themselves before calling this.
  const loadProvinces = useCallback(() => {
    fetchProvinces()
      .then((data) => {
        setProvinces(data)
        setProvincesStatus('ready')
      })
      .catch(() => setProvincesStatus('error'))
  }, [])

  useEffect(() => {
    loadProvinces()
  }, [loadProvinces])

  const retryProvinces = () => {
    setProvincesStatus('loading')
    loadProvinces()
  }

  const loadWards = useCallback(
    (cityName) => {
      const province = provinceByName[cityName]
      if (!province) return
      fetchProvinceWithWards(province.officialCode)
        .then((data) => {
          setWards(data.wards ?? [])
          setWardsStatus('ready')
        })
        .catch(() => setWardsStatus('error'))
    },
    [provinceByName],
  )

  const retryWards = () => {
    setWardsStatus('loading')
    loadWards(city)
  }

  // City is only ever changed by this handler, so the ward fetch is triggered
  // directly here instead of watched for via an effect.
  const handleCityChange = (value) => {
    setCity(value)
    setWard('')
    setWards([])
    if (value) {
      setWardsStatus('loading')
      loadWards(value)
    } else {
      setWardsStatus('idle')
    }
  }

  const handleWardChange = (value) => {
    setWard(value)
  }

  const cityPlaceholder =
    provincesStatus === 'loading'
      ? 'Loading provinces…'
      : provincesStatus === 'error'
        ? "Couldn't load provinces"
        : 'Select city / province'

  const wardPlaceholder = !city
    ? 'Select city first'
    : wardsStatus === 'loading'
      ? 'Loading wards…'
      : wardsStatus === 'error'
        ? "Couldn't load wards"
        : 'Select ward'

  return {
    city,
    cityCode,
    ward,
    provinces,
    wards,
    provincesStatus,
    wardsStatus,
    cityPlaceholder,
    wardPlaceholder,
    handleCityChange,
    handleWardChange,
    retryProvinces,
    retryWards,
  }
}
