package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.modules.notification.dto.internal.StaffWelcomeEmailCommand;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * The welcome letter itself (Req 3.1.S5): the issue names five things it must carry — the
 * staff member's name, role, email, initial password and a sign-in URL — and each is asserted
 * here by what a reader would see, not by the template's wording.
 */
class StaffWelcomeEmailComposerTest {

	private static final String SIGN_IN_URL = "https://smp.fixinh.vn/login";

	/** Nguyễn Minh Tuấn, escaped so the assertion cannot depend on how this file was saved. */
	private static final String NAME = "Nguy\u1ec5n Minh Tu\u1ea5n";

	private static final String PASSWORD = "Xk3m-Pq9a-Z2vT";

	private final StaffWelcomeEmailComposer composer = new StaffWelcomeEmailComposer(properties());

	@Test
	void theLetterCarriesEverythingTheNewStaffMemberNeeds() {
		EmailMessage message = composer.compose(command("TECHNICIAN"));

		assertEquals("tuan@fixinh.vn", message.to());
		assertEquals("Welcome to Fixinh", message.subject());

		String body = message.body();
		assertTrue(body.startsWith("Hello " + NAME + ","), "the greeting, diacritics and all");
		assertTrue(body.contains("Role:      Technician"), "the role, as a job rather than a code");
		assertTrue(body.contains("Email:     tuan@fixinh.vn"), "the address they sign in with");
		assertTrue(body.contains("Password:  " + PASSWORD), "the initial password SMP set");
		assertTrue(body.contains("Sign in at " + SIGN_IN_URL), "and where to use them");
	}

	@Test
	void writesAReceptionistAsAJobToo() {
		assertTrue(composer.compose(command("RECEPTIONIST")).body()
				.contains("Role:      Receptionist"));
	}

	@Test
	void theStoredSummaryNamesTheRecipientButNeverThePassword() {
		// The summary is the notification's message, and it outlives delivery; the letter does not.
		String summary = composer.summaryFor(command("TECHNICIAN"));

		assertTrue(summary.contains("tuan@fixinh.vn"));
		assertFalse(summary.contains(PASSWORD));
	}

	@Test
	void onlyTheWelcomeEmailHasASubject() {
		assertEquals("Welcome to Fixinh",
				composer.subjectFor(NotificationType.STAFF_ACCOUNT_CREATED));
		assertThrows(IllegalArgumentException.class,
				() -> composer.subjectFor(NotificationType.BOOKING_CONFIRMED));
		assertThrows(IllegalArgumentException.class,
				() -> composer.subjectFor(NotificationType.BOOKING_SCHEDULED));
	}

	private StaffWelcomeEmailCommand command(String role) {
		return new StaffWelcomeEmailCommand("TEC-7F3A2C", NAME, "tuan@fixinh.vn", role, PASSWORD,
				"HCM");
	}

	private static SbpProperties properties() {
		SbpProperties properties = new SbpProperties();
		properties.getEmail().setStaffSignInUrl(SIGN_IN_URL);
		return properties;
	}
}