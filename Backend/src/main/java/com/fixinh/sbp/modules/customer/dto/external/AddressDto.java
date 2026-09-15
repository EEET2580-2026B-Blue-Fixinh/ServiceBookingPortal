package com.fixinh.sbp.modules.customer.dto.external;

import com.fixinh.sbp.modules.customer.model.Address;

/**
 * {@link Address}.
 *
 * <p>{@code provinceName} is denormalised in for callers that need a display name alongside the
 * code (API Documentation Section 6.1) — null wherever a caller has no province catalogue to
 * hand, such as {@link #from(Address)}.
 */
public record AddressDto(
		String streetNumber,
		String streetName,
		String ward,
		String provinceCode,
		String provinceName) {

	public static AddressDto from(Address address) {
		return from(address, null);
	}

	public static AddressDto from(Address address, String provinceName) {
		if (address == null) {
			return null;
		}
		return new AddressDto(
				address.getStreetNumber(),
				address.getStreetName(),
				address.getWard(),
				address.getProvinceCode(),
				provinceName);
	}
}