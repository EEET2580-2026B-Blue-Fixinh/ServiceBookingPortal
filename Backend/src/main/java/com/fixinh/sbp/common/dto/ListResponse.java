package com.fixinh.sbp.common.dto;

import java.util.List;

/**
 * An unpaged collection: {@code { "items": [ ... ] }} (API Documentation Section 2.5).
 *
 * <p>Collections are always wrapped in an object rather than returned as a bare array, so
 * paging or a {@code lastSyncedAt} can be added later without changing the shape clients
 * already parse. Use {@link PageResponse} where a page object is also required.
 */
public record ListResponse<T>(List<T> items) {
}
