package com.fixinh.sbp.modules.reference.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fixinh.sbp.common.sharding.Region;

import lombok.Getter;

/**
 * The provinces and municipalities of Vietnam — the single source of truth behind
 * {@code GET /reference/provinces} (Req 3.1.S4) and behind shard routing (Req 3.1.U1).
 *
 * <p>This is the post-2025 structure: the reform merged 63 provinces into 34 and abolished
 * the district tier, so a province contains wards directly.
 *
 * <p><b>Three identifiers, on purpose:</b>
 * <ul>
 *   <li>The <b>enum name</b> is the {@code provinceCode} the API speaks — short and readable,
 *       so {@code HCM} rather than {@code 79} appears in requests, documents and logs. These
 *       are project-defined; {@code HCM}, {@code HN} and {@code HUE} come from the API
 *       Documentation's own examples and the rest follow the same initials pattern.</li>
 *   <li>{@link #officialCode} is Vietnam's official province code (mã tỉnh) from the General
 *       Statistics Office. Clients need it to look up wards against the public address API,
 *       which keys on it. Never persisted as the province identifier — {@code provinceCode} is.</li>
 *   <li>{@link #displayName} is what the customer sees in the dropdown.</li>
 * </ul>
 *
 * <p><b>Region boundary</b> (Req 3.1.U1): Huế and everything south is {@link Region#SOUTH};
 * Quảng Trị and everything north is {@link Region#NORTH}. Listed per province rather than
 * derived from the code ordering — a table can be audited against a map, whereas an inequality
 * silently misfiles a customer the day the numbering changes.
 */
@Getter
public enum Province {

	// --- Northern store: Quảng Trị and north ---
	HN("1", "Thành phố Hà Nội", true, Region.NORTH),
	CB("4", "Tỉnh Cao Bằng", false, Region.NORTH),
	TQ("8", "Tỉnh Tuyên Quang", false, Region.NORTH),
	DB("11", "Tỉnh Điện Biên", false, Region.NORTH),
	LCH("12", "Tỉnh Lai Châu", false, Region.NORTH),
	SL("14", "Tỉnh Sơn La", false, Region.NORTH),
	LCI("15", "Tỉnh Lào Cai", false, Region.NORTH),
	TN("19", "Tỉnh Thái Nguyên", false, Region.NORTH),
	LS("20", "Tỉnh Lạng Sơn", false, Region.NORTH),
	QN("22", "Tỉnh Quảng Ninh", false, Region.NORTH),
	BN("24", "Tỉnh Bắc Ninh", false, Region.NORTH),
	PT("25", "Tỉnh Phú Thọ", false, Region.NORTH),
	HP("31", "Thành phố Hải Phòng", true, Region.NORTH),
	HY("33", "Tỉnh Hưng Yên", false, Region.NORTH),
	NB("37", "Tỉnh Ninh Bình", false, Region.NORTH),
	TH("38", "Tỉnh Thanh Hóa", false, Region.NORTH),
	NA("40", "Tỉnh Nghệ An", false, Region.NORTH),
	HT("42", "Tỉnh Hà Tĩnh", false, Region.NORTH),
	QT("44", "Tỉnh Quảng Trị", false, Region.NORTH),

	// --- Southern store: Huế and south ---
	HUE("46", "Thành phố Huế", true, Region.SOUTH),
	DN("48", "Thành phố Đà Nẵng", true, Region.SOUTH),
	QNG("51", "Tỉnh Quảng Ngãi", false, Region.SOUTH),
	GL("52", "Tỉnh Gia Lai", false, Region.SOUTH),
	KH("56", "Tỉnh Khánh Hòa", false, Region.SOUTH),
	DL("66", "Tỉnh Đắk Lắk", false, Region.SOUTH),
	LD("68", "Tỉnh Lâm Đồng", false, Region.SOUTH),
	DNA("75", "Tỉnh Đồng Nai", false, Region.SOUTH),
	HCM("79", "Thành phố Hồ Chí Minh", true, Region.SOUTH),
	TNI("80", "Tỉnh Tây Ninh", false, Region.SOUTH),
	DT("82", "Tỉnh Đồng Tháp", false, Region.SOUTH),
	VL("86", "Tỉnh Vĩnh Long", false, Region.SOUTH),
	AG("91", "Tỉnh An Giang", false, Region.SOUTH),
	CT("92", "Thành phố Cần Thơ", true, Region.SOUTH),
	CM("96", "Tỉnh Cà Mau", false, Region.SOUTH);

	private final String officialCode;
	private final String displayName;
	private final boolean municipality;
	private final Region region;

	private static final Map<String, Province> BY_CODE = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(Province::name, Function.identity()));

	Province(String officialCode, String displayName, boolean municipality, Region region) {
		this.officialCode = officialCode;
		this.displayName = displayName;
		this.municipality = municipality;
		this.region = region;
	}

	/** The API-facing code, e.g. {@code HCM}. */
	public String getProvinceCode() {
		return name();
	}

	/**
	 * @return the province, or empty when the code is not one we publish. Callers reject
	 *         unknown codes rather than guessing — a wrong guess files a customer in the
	 *         wrong regional store.
	 */
	public static Optional<Province> byCode(String provinceCode) {
		if (provinceCode == null || provinceCode.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_CODE.get(provinceCode.trim().toUpperCase(Locale.ROOT)));
	}
}
