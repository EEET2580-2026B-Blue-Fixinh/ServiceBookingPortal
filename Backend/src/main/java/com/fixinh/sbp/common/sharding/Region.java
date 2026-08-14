package com.fixinh.sbp.common.sharding;

/**
 * The two regional stores customer data is split across (Req 3.1.U1).
 *
 * <p>Boundary: Hue and everything south of it is {@link #SOUTH}; Quang Tri and everything
 * north of it is {@link #NORTH}.
 */
public enum Region {
	NORTH,
	SOUTH;

	/** The other shard. Used when writing the login-lookup replica (see CustomerRepository). */
	public Region opposite() {
		return this == NORTH ? SOUTH : NORTH;
	}
}
