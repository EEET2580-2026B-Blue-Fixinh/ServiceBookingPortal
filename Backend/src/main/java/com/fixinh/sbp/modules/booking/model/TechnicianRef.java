package com.fixinh.sbp.modules.booking.model;
/**
 * A technician assigned to a booking, as SBP knows them (API Documentation §7.2, §7.3).
 *
 * <p>Identifier and full name only — SBP has no reason to hold anything else about a technician
 * (§12.6's own note). Populated once SMP assigns technicians to a booking and pushes that change
 * through §11.7; empty until then.
 */
public record TechnicianRef(String technicianId, String fullName) {
}
