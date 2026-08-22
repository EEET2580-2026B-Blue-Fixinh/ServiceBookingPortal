package com.fixinh.sbp.modules.auth.service;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.auth.interfaces.internal.CredentialRegionUpdater;
import com.fixinh.sbp.modules.auth.model.AuthProvider;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.auth.repository.UserCredentialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CredentialRegionService implements CredentialRegionUpdater {
	private final UserCredentialRepository credentials;

	@Override
	public void relocateGoogleCredential(String userId, Region targetRegion) {
		UserCredential credential = credentials.findById(userId)
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
		if (credential.getAuthProviders() == null
				|| !credential.getAuthProviders().contains(AuthProvider.GOOGLE)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED);
		}
		Region previousRegion = credential.getHomeRegion();
		credential.setHomeRegion(targetRegion);
		credentials.relocate(credential, previousRegion);
	}
}
