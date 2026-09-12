package com.fixinh.sbp.common.email.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;

/**
 * Plain unit tests — no Spring context. The client's only collaborators are the filesystem and
 * a properties object, so starting a context would slow the suite down and prove nothing extra.
 */
class FileWritingEmailClientTest {

	private FileWritingEmailClient clientWritingTo(Path outbox) {
		SbpProperties properties = new SbpProperties();
		properties.getEmail().setOutboxDirectory(outbox.toString());
		properties.getEmail().setFromAddress("no-reply@fixinh.vn");
		properties.getEmail().setFromName("Fixinh");

		return new FileWritingEmailClient(properties);
	}

	@Test
	void writesOneReadableMessagePerSend(@TempDir Path tempDir) throws IOException {
		Path outbox = tempDir.resolve("mail");
		clientWritingTo(outbox).send(new EmailMessage(
				"david.nguyen@fixinh.vn", "Welcome to Fixinh", "Your account is ready."));

		List<Path> written;
		try (var files = Files.list(outbox)) {
			written = files.toList();
		}
		assertEquals(1, written.size(), "one send should produce exactly one file");

		String content = Files.readString(written.get(0), StandardCharsets.UTF_8);
		assertTrue(content.contains("To: david.nguyen@fixinh.vn"), "recipient header");
		assertTrue(content.contains("Subject: Welcome to Fixinh"), "subject header");
		assertTrue(content.contains("\n\nYour account is ready."),
				"a blank line must separate headers from body, or mail clients show nothing");
	}

	@Test
	void keepsVietnameseCharactersIntact(@TempDir Path tempDir) throws IOException {
		// "Xin chào Nguyễn Minh Tuấn", escaped so the test cannot depend on how this file was
		// saved. The earlier plain-ASCII version passed whatever the client did to the text.
		String greeting = "Xin ch\u00e0o Nguy\u1ec5n Minh Tu\u1ea5n";

		Path outbox = tempDir.resolve("mail");
		clientWritingTo(outbox).send(new EmailMessage("tuan@fixinh.vn", "Chao mung", greeting));

		try (var files = Files.list(outbox)) {
			String content = Files.readString(files.toList().get(0), StandardCharsets.UTF_8);
			assertTrue(content.contains(greeting), "read back as UTF-8, every diacritic survives");
		}
	}

	@Test
	void producesAFilenameWindowsAccepts(@TempDir Path tempDir) throws IOException {
		Path outbox = tempDir.resolve("mail");
		clientWritingTo(outbox).send(
				new EmailMessage("a.b@fixinh.vn", "Subject", "Body"));

		try (var files = Files.list(outbox)) {
			String name = files.toList().get(0).getFileName().toString();
			// The characters Windows refuses. An address contains '@', and a naive
			// ISO timestamp contains ':' -- either would fail only on a teammate's machine.
			assertTrue(name.matches("[A-Za-z0-9._-]+\\.eml"),
					"unsafe characters in filename: " + name);
		}
	}

	@Test
	void reportsDeliveryFailureWhenTheOutboxCannotBeWritten(@TempDir Path tempDir)
			throws IOException {

		// A file sitting where the outbox directory should be. createDirectories then fails,
		// which is the cheapest cross-platform way to simulate a provider outage -- and the
		// hook the 502 EMAIL_DELIVERY_FAILED test uses.
		Path blocked = tempDir.resolve("mail");
		Files.createFile(blocked);

		EmailDeliveryException thrown = assertThrows(EmailDeliveryException.class,
				() -> clientWritingTo(blocked).send(
						new EmailMessage("a@fixinh.vn", "Subject", "Body")));

		assertTrue(thrown.getMessage().contains("mail"), "message should name the path");
	}

	@Test
	void rejectsAMessageWithNoRecipient() {
		assertThrows(IllegalArgumentException.class,
				() -> new EmailMessage("  ", "Subject", "Body"));
	}
}