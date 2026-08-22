package com.fixinh.sbp.common.dto;

import java.util.List;

/**
 * A paged collection: {@code { "items": [ ... ], "page": { ... } }} 
 */
public record PageResponse<T>(List<T> items, Metadata page) {

	/** Section 2.6: {@code size} defaults to 20 when the client does not ask for one. */
	public static final int DEFAULT_SIZE = 20;

	/** Section 2.6: {@code size} is capped at 100, however large a value the client sends. */
	public static final int MAX_SIZE = 100;

	public static <T> PageResponse<T> of(List<T> items, int number, int size,
			long totalElements) {
		return new PageResponse<>(items, Metadata.of(number, size, totalElements));
	}

	/**
	 * A client's {@code page} reduced to something usable: never negative, and null means the
	 * first page.
	 *
	 * <p>Clamped rather than rejected. A negative page is a client bug, not a user error, and
	 * Section 9.1 defines no validation error for one — so the first page is the answer that
	 * keeps the bell menu working while the bug is found.
	 */
	public static int normalisePage(Integer page) {
		return page == null || page < 0 ? 0 : page;
	}

	/**
	 * A client's {@code size} reduced to the range Section 2.6 permits.
	 *
	 * <p>The cap is the point: without it a caller could ask for every notification ever
	 * written in one request, and both regional stores would build that list in memory.
	 */
	public static int normaliseSize(Integer size) {
		if (size == null || size < 1) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}

	/**
	 * The page object itself, identical in every paged response in the API.
	 *
	 * @param number        zero-based, per Section 2.6
	 * @param size          the size actually used — the requested size after
	 *                      {@link #normaliseSize}, not the raw value the client sent
	 * @param totalElements across the whole collection, not this page. A long because it
	 *                      counts documents, and a count is not bounded by a page size
	 * @param totalPages    derived from the two above; never stored, never passed in
	 */
	public record Metadata(int number, int size, long totalElements, int totalPages) {

		/**
		 * @throws IllegalArgumentException if {@code size} is not positive. Callers are
		 *         expected to have passed the requested size through {@link #normaliseSize}
		 *         first, so reaching this means a page size was invented somewhere else.
		 */
		public static Metadata of(int number, int size, long totalElements) {
			if (size < 1) {
				throw new IllegalArgumentException(
						"Page size must be at least 1, so normaliseSize should have been "
								+ "applied before this point. Got: " + size);
			}

			// Integer ceiling division. The obvious Math.ceil on doubles loses precision
			// once totalElements is large, and this counts documents, which is exactly the
			// number that grows.
			int totalPages = (int) ((totalElements + size - 1) / size);

			return new Metadata(number, size, totalElements, totalPages);
		}
	}
}