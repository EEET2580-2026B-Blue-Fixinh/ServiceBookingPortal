package com.fixinh.sbp.common.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailMessageTest {

	@Test
	void printingAMessageWithholdsItsBody() {
		// What a failing Mockito verification or a careless log line would print. Before the
		// override this showed the whole letter, initial password included.
		EmailMessage message = new EmailMessage("tuan@fixinh.vn", "Welcome to Fixinh",
				"Hello Tuan,\n  Password:  Xk3m-Pq9a-Z2vT\n");

		String printed = message.toString();

		assertFalse(printed.contains("Xk3m-Pq9a-Z2vT"), "the body carries the initial password");
		assertTrue(printed.contains("tuan@fixinh.vn"), "but the rest stays useful for debugging");
		assertTrue(printed.contains("Welcome to Fixinh"));
	}
}