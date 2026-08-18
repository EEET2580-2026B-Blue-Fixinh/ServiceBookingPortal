package com.fixinh.sbp.modules.auth.service;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.admin.interfaces.internal.AdminInternalService;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.customer.interfaces.internal.CustomerInternalService;

import lombok.RequiredArgsConstructor;

/**
 * Follows a credential's discriminator to the profile collection that holds the rest of it.
 *
 * <p>This is the one place that knows the mapping from {@code accountRole} to a profile store.
 * Adding a third kind of account means adding a branch here and nowhere else in the auth module.
 *
 * <p>Only the display name is read today, because that is all the sign-in response carries.
 */
@Component
@RequiredArgsConstructor
public class AccountProfileReader {

	private final CustomerInternalService customerService;
	private final AdminInternalService adminService;

	/**
	 * @return the profile's full name, or the email if no profile exists. A credential without
	 *         its profile is a broken pair rather than a normal state, but sign-in has already
	 *         succeeded by this point — failing the request would deny a caller whose password
	 *         was correct, over a display string.
	 */
	public String fullNameOf(UserCredential credential) {
		String id = credential.getId();

		return switch (credential.getAccountRole()) {
			case CUSTOMER -> customerService.findById(id)
					.map(profile -> profile.getFullName())
					.orElseGet(credential::getEmail);
			case ADMIN -> adminService.findById(id)
					.map(profile -> profile.getFullName())
					.orElseGet(credential::getEmail);
		};
	}
}
