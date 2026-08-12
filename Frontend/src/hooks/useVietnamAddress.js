import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchProvinces, fetchProvinceWithDistrictsAndWards } from '../services/vietnamAddressApi'

// Owns the cascading city/district/ward selection for Vietnam addresses:
// fetches provinces on mount, fetches a province's districts+wards whenever
// its city changes, and resets the narrower fields when a broader one changes.
export function useVietnamAddress() {
  const [city, setCity] = useState('')
  const [district, setDistrict] = useState('')
  const [ward, setWard] = useState('')

  const [provinces, setProvinces] = useState([])
  const [provincesStatus, setProvincesStatus] = useState('loading')

  const [districts, setDistricts] = useState([])
  const [districtsStatus, setDistrictsStatus] = useState('idle')

  const provinceByName = useMemo(
    () => Object.fromEntries(provinces.map((province) => [province.name, province])),
    [provinces],
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

  const loadDistricts = useCallback(
    (cityName) => {
      const province = provinceByName[cityName]
      if (!province) return
      fetchProvinceWithDistrictsAndWards(province.code)
        .then((data) => {
          setDistricts(data.districts ?? [])
          setDistrictsStatus('ready')
        })
        .catch(() => setDistrictsStatus('error'))
    },
    [provinceByName],
  )

  const retryDistricts = () => {
    setDistrictsStatus('loading')
    loadDistricts(city)
  }

  // City is only ever changed by this handler, so the district fetch is
  // triggered directly here instead of watched for via an effect.
  const handleCityChange = (value) => {
    setCity(value)
    setDistrict('')
    setWard('')
    setDistricts([])
    if (value) {
      setDistrictsStatus('loading')
      loadDistricts(value)
    } else {
      setDistrictsStatus('idle')
    }
  }

  const handleDistrictChange = (value) => {
    setDistrict(value)
    setWard('')
  }

  const handleWardChange = (value) => {
    setWard(value)
  }

  const wards = useMemo(
    () => districts.find((d) => d.name === district)?.wards ?? [],
    [districts, district],
  )

  const cityPlaceholder =
    provincesStatus === 'loading'
      ? 'Loading provinces…'
      : provincesStatus === 'error'
        ? "Couldn't load provinces"
        : 'Select city / province'

  const districtPlaceholder = !city
    ? 'Select province first'
    : districtsStatus === 'loading'
      ? 'Loading districts…'
      : districtsStatus === 'error'
        ? "Couldn't load districts"
        : 'Select district'

  const wardPlaceholder = !district ? 'Select district first' : 'Select ward'

  return {
    city,
    district,
    ward,
    provinces,
    districts,
    wards,
    provincesStatus,
    districtsStatus,
    cityPlaceholder,
    districtPlaceholder,
    wardPlaceholder,
    handleCityChange,
    handleDistrictChange,
    handleWardChange,
    retryProvinces,
    retryDistricts,
  }
}
