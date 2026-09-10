package com.fixinh.sbp.modules.reference.service;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;
import com.fixinh.sbp.modules.reference.model.RepairServiceType;
import com.fixinh.sbp.modules.reference.repository.RepairServiceTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds SBP's local repair-service catalogue on every boot (Phase 3.1, "seed database with
 * service catalog"; API Documentation §5.2, §12.5).
 *
 * <p>Upserts by {@code serviceId} rather than inserting blindly, so restarting the app never
 * leaves stale duplicates behind, and then deletes whatever SMP no longer lists — SBP's copy is
 * a replica, so it has to be able to shrink as well as grow. Runs against whichever
 * {@link SmpBookingClient} is active, so nothing here changes when that switch flips.
 *
 * <p>{@code description} is never touched by a sync: SMP's catalogue carries no such field (see
 * {@link SmpRepairServiceDto}), so an existing entry's copy is preserved across restarts rather
 * than being reset to blank every time.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RepairServiceCatalogSeeder implements ApplicationRunner {

	private final SmpBookingClient smpBookingClient;
	private final RepairServiceTypeRepository repository;
	private final RepairServiceCatalogSyncStatus syncStatus;

	@Override
	public void run(ApplicationArguments args) {
		List<SmpRepairServiceDto> catalogue = smpBookingClient.listRepairServices();

		for (SmpRepairServiceDto entry : catalogue) {
			RepairServiceType existing = repository.findByServiceId(entry.serviceId()).orElse(null);
			repository.save(toEntity(entry, existing));
		}

		long removed = pruneEntriesSmpNoLongerHas(catalogue);

		syncStatus.recordSync(Instant.now());

		log.info("Repair-service catalogue seeded: {} entr{} synced from SMP, {} removed.",
				catalogue.size(), catalogue.size() == 1 ? "y" : "ies", removed);
	}

	/**
	 * Deletes what SMP no longer lists. Upserting alone lets the replica grow but never shrink,
	 * so a service SMP drops — or, as happened on the switch from the mock catalogue to the live
	 * one, a whole set of identifiers that only ever existed locally — stays bookable in SBP
	 * forever.
	 *
	 * <p>Safe for bookings already made against a removed service: {@code ServiceBooking} stores
	 * its own {@code serviceName} and {@code baseServiceFee} at creation time and never reads
	 * them back from this collection.
	 *
	 * <p>An <b>empty</b> catalogue prunes nothing. SMP returning nothing at all is far more
	 * likely to be a fault on their side than a real answer, and wiping the collection on it
	 * would take the booking screen down with it.
	 */
	private long pruneEntriesSmpNoLongerHas(List<SmpRepairServiceDto> catalogue) {
		if (catalogue.isEmpty()) {
			log.warn("SMP returned an empty catalogue; keeping the existing entries rather than "
					+ "clearing the collection.");
			return 0;
		}

		List<String> known = catalogue.stream().map(SmpRepairServiceDto::serviceId).toList();
		return repository.deleteByServiceIdNotIn(known);
	}

	/**
	 * Carries {@code id} and {@code description} over from {@code existing} when present, so the
	 * save updates the same document in place instead of inserting a duplicate.
	 */
	private RepairServiceType toEntity(SmpRepairServiceDto entry, RepairServiceType existing) {
		RepairServiceType.RepairServiceTypeBuilder builder = RepairServiceType.builder()
				.serviceId(entry.serviceId())
				.serviceName(entry.serviceName())
				.categoryName(entry.categoryName())
				.baseServiceFee(entry.baseServiceFee())
				.standardDurationMinutes(entry.standardDurationMinutes())
				.active(entry.active());

		if (existing != null) {
			builder.id(existing.getId()).description(existing.getDescription());
		}

		return builder.build();
	}
}
