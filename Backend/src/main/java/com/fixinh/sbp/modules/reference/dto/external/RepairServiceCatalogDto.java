package com.fixinh.sbp.modules.reference.dto.external;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for {@code GET /reference/repair-services} (API Documentation §5.2).
 *
 * <p>Not the generic {@link com.fixinh.sbp.common.dto.ListResponse} — this collection carries
 * {@code lastSyncedAt} alongside {@code items} so the interface can say how current the prices
 * are instead of implying they are live (§5.2's own note).
 */
public record RepairServiceCatalogDto(List<ServiceTypeDto> items, Instant lastSyncedAt) {
}
