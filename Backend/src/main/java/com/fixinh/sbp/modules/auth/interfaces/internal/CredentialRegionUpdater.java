package com.fixinh.sbp.modules.auth.interfaces.internal;

import com.fixinh.sbp.common.sharding.Region;

/** Narrow hook used when the customer module changes the shard owning a profile. */
public interface CredentialRegionUpdater {
	void relocateGoogleCredential(String userId, Region targetRegion);
}
