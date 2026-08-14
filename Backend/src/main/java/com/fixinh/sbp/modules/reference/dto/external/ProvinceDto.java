package com.fixinh.sbp.modules.reference.dto.external;

/**
 * One entry in the address dropdown (API Documentation Section 5.1).
 *
 * <p>The region a province belongs to is deliberately <b>not</b> returned. Routing is a server
 * concern; publishing it would invite clients to depend on the split (Req 3.1.U2).
 *
 * @param provinceCode  what the client sends back as {@code address.provinceCode}, e.g. HCM
 * @param provinceName  the label to show
 * @param isMunicipality whether this is a centrally-governed city (thành phố trung ương)
 * @param officialCode  Vietnam's official province code (mã tỉnh). Present so a client can look
 *                      up wards against the public address API, which keys on it. It is not the
 *                      province's identity here — {@code provinceCode} is.
 */
public record ProvinceDto(
		String provinceCode,
		String provinceName,
		boolean isMunicipality,
		String officialCode) {
}
