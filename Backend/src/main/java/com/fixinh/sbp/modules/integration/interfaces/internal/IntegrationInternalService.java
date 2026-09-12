package com.fixinh.sbp.modules.integration.interfaces.internal;

/**
 * What other SBP modules may ask of the integration module.
 *
 * <p>Empty by design. Integration is driven from outside — SMP calls in, and the modules that
 * care learn of it through {@code BookingStatusChangedEvent} rather than by calling here. A
 * method on this interface would be a second, synchronous path into the same work.
 */
public interface IntegrationInternalService {
}
