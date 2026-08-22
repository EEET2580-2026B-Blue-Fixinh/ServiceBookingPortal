package com.fixinh.sbp.modules.customer.dto.external;

import com.fixinh.sbp.modules.customer.model.Address;

/** {@link Address} */
public record AddressDto(
		String streetNumber,
		String streetName,
		String ward,
		String provinceCode) {

	public static AddressDto from(Address address) {
		if (address == null) {
			return null;
		}
		return new AddressDto(
				address.getStreetNumber(),
				address.getStreetName(),
				address.getWard(),
				address.getProvinceCode());
	}
}