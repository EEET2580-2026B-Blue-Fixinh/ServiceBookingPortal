package com.fixinh.sbp.modules.notification.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.RegionResolver;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.common.sharding.SubjectType;
import com.fixinh.sbp.modules.notification.model.RecipientType;

import lombok.RequiredArgsConstructor;

/**
 * Decides which store a notification belongs in (Region type)
 */
@Component
@RequiredArgsConstructor
public class NotificationRegionResolver {

	private final ShardDirectoryRepository shardDirectory;
	private final RegionResolver regionResolver;

	/**
	 * The store this recipient's notifications belong in, registering the subject if the
	 * directory has not seen it before.
	 *
	 * <p>One rule for both recipient types: <b>ask the directory; on a miss, resolve the
	 * province and remember the answer.</b> It reads sensibly either way — for a staff member
	 * the province is their branch, for a customer it is their address.
	 *
	 * @param provinceCode only consulted when the directory has no entry. Ignored otherwise,
	 *        deliberately: once a subject is placed, its data is in that store, and a later
	 *        province that disagrees does not move it. Correcting a misplacement is a
	 *        migration, not a re-resolve.
	 * @throws ApiException {@code 400 PROVINCE_CODE_INVALID} when the subject is unknown and
	 *         the province cannot supply a region. Rejecting beats defaulting — a notification
	 *         filed in the wrong store is invisible to its recipient, and nothing reports it.
	 */
	public Region regionFor(RecipientType recipientType, String recipientIdentifier,
			String provinceCode) {

		if (recipientType == null || recipientIdentifier == null || recipientIdentifier.isBlank()) {
			throw new IllegalArgumentException(
					"A notification needs a recipient type and identifier before it can be filed.");
		}

		SubjectType subjectType = subjectTypeFor(recipientType);

		Optional<Region> alreadyKnown = shardDirectory.findRegion(subjectType, recipientIdentifier);
		if (alreadyKnown.isPresent()) {
			return alreadyKnown.get();
		}

		if (provinceCode == null || provinceCode.isBlank()) {
			throw new ApiException(ErrorCode.PROVINCE_CODE_INVALID,
					"No province was supplied, and " + recipientIdentifier
							+ " is not in the shard directory, so there is no store to file this "
							+ "notification in.");
		}
		if (!regionResolver.isKnown(provinceCode)) {
			throw new ApiException(ErrorCode.PROVINCE_CODE_INVALID);
		}

		// register returns the region it recorded, so the miss path reads as one expression.
		return shardDirectory.register(subjectType, recipientIdentifier,
				regionResolver.resolve(provinceCode));
	}

	/**
	 * No {@code default} branch, on purpose: adding a recipient type should break this build
	 * rather than quietly route the new kind of recipient as a customer.
	 */
	private SubjectType subjectTypeFor(RecipientType recipientType) {
		return switch (recipientType) {
			case CUSTOMER -> SubjectType.CUSTOMER;
			case STAFF -> SubjectType.STAFF;
		};
	}
}