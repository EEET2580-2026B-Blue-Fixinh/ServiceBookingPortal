package com.fixinh.sbp.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The paging arithmetic every list endpoint depends on (API Documentation Sections 2.5, 2.6).
 *
 * <p>Worth its own class because the numbers are shared: a mistake here is wrong in the
 * notification bell, the admin customer list and everything paged that comes after.
 */
class PageResponseTest {

	@Test
	void matchesTheWorkedExamplesInTheApiDocumentation() {
		assertEquals(2, PageResponse.Metadata.of(0, 20, 37).totalPages(), "Section 2.5");
		assertEquals(1, PageResponse.Metadata.of(0, 20, 1).totalPages(), "Section 9.1");
	}

	@Test
	void countsAnEmptyCollectionAsNoPagesAtAll() {
		// The common case at launch: a bell nobody has been sent anything for.
		assertEquals(0, PageResponse.Metadata.of(0, 20, 0).totalPages());
	}

	@Test
	void doesNotRoundAnExactMultipleUp() {
		assertEquals(2, PageResponse.Metadata.of(0, 20, 40).totalPages());
		assertEquals(3, PageResponse.Metadata.of(0, 20, 41).totalPages(), "but one over does");
	}

	@Test
	void staysExactAtCountsThatOverflowDoublePrecision() {
		// Integer ceiling division, not Math.ceil on doubles. Document counts are exactly the
		// number that grows until the obvious implementation starts lying.
		assertEquals(100_000_001, PageResponse.Metadata.of(0, 20, 2_000_000_001L).totalPages());
	}

	@Test
	void fallsBackToTwentyWhenNoUsableSizeWasAskedFor() {
		assertEquals(20, PageResponse.normaliseSize(null));
		assertEquals(20, PageResponse.normaliseSize(0));
		assertEquals(20, PageResponse.normaliseSize(-5));
	}

	@Test
	void capsTheSizeAtOneHundred() {
		// Section 2.6. Without the cap a caller could ask a regional store to build every
		// notification ever written into one response.
		assertEquals(50, PageResponse.normaliseSize(50));
		assertEquals(100, PageResponse.normaliseSize(500));
		assertEquals(100, PageResponse.normaliseSize(Integer.MAX_VALUE));
	}

	@Test
	void clampsANegativePageRatherThanRejectingIt() {
		// A negative page is a client bug, and Section 9.1 defines no error for one. Answering
		// with the first page keeps the bell working while the bug is found.
		assertEquals(0, PageResponse.normalisePage(null));
		assertEquals(0, PageResponse.normalisePage(-3));
		assertEquals(4, PageResponse.normalisePage(4));
	}

	@Test
	void refusesToDivideByAPageSizeOfZero() {
		// Reaching this means a size was invented somewhere that skipped normaliseSize.
		assertThrows(IllegalArgumentException.class, () -> PageResponse.Metadata.of(0, 0, 10));
	}
}