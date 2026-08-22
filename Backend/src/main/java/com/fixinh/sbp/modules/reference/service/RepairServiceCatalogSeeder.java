package com.fixinh.sbp.modules.reference.service;

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
 * <p>Upserts by {@code serviceId} rather than inserting blindly, so restarting the app — or the
 * fixed catalogue in {@code MockSmpBookingClient} changing later — never leaves stale duplicates
 * behind. Runs against whichever {@link SmpBookingClient} is active (mock today, live once §12 is
 * agreed — see §13.1), so nothing here changes when that switch flips.
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

	@Override
	public void run(ApplicationArguments args) {
		List<SmpRepairServiceDto> catalogue = smpBookingClient.listRepairServices();

		for (SmpRepairServiceDto entry : catalogue) {
			RepairServiceType existing = repository.findByServiceId(entry.serviceId()).orElse(null);
			repository.save(toEntity(entry, existing));
		}

		log.info("Repair-service catalogue seeded: {} entr{} synced from SMP.",
				catalogue.size(), catalogue.size() == 1 ? "y" : "ies");
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
