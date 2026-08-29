package com.fixinh.sbp.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.fixinh.sbp.common.config.SbpProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes uploaded images to disk instead of publishing them, so the cancellation flow of §7.4
 * runs end to end without a Cloudinary account.
 *
 * <p>Chosen when {@code fixinh.sbp.images.mode} is {@code local}, and when the property is
 * absent entirely. Absence matters for the same reason it does in {@code FileWritingEmailClient}:
 * an environment nobody has configured must land on the implementation that publishes nothing.
 *
 * <p><b>The URLs are honest, not renderable.</b> A stored image comes back as a {@code file:}
 * URL naming a real path on this machine. It will not load in a browser, exactly as a message
 * written by the {@code file} email client is a real message that was never delivered. The
 * alternative — inventing an {@code https://} URL that resolves to nothing — would put a
 * plausible-looking lie in the database and in the payload sent to SMP.
 *
 * <p><b>The directory is a test hook.</b> Point {@code fixinh.sbp.images.local-directory} at
 * something unwritable and every upload raises {@link ImageUploadException}. That is how §7.4's
 * 502 {@code UPLOAD_FAILED} branch is exercised without breaking anything real.
 */
@Component
@ConditionalOnProperty(prefix = "fixinh.sbp.images", name = "mode", havingValue = "local",
		matchIfMissing = true)
@Slf4j
public class LocalImageStorageService implements ImageStorageService {

	private final SbpProperties.Images properties;

	public LocalImageStorageService(SbpProperties properties) {
		this.properties = properties.getImages();
	}

	/** Which implementation is running should be answerable from the startup log alone. */
	@PostConstruct
	void announceMode() {
		log.info("Image storage: LOCAL mode. Nothing is published - images are written to {}",
				Path.of(properties.getLocalDirectory()).toAbsolutePath());
	}

	@Override
	public List<String> store(String folder, String baseName, List<MultipartFile> images) {
		if (images == null || images.isEmpty()) {
			return List.of();
		}

		Path directory = Path.of(properties.getLocalDirectory()).resolve(folder);
		List<String> urls = new ArrayList<>(images.size());

		for (int index = 0; index < images.size(); index++) {
			MultipartFile image = images.get(index);
			// Numbered from one so the names read the way §7.4's example URL does, and so two
			// uploads of the same photograph do not collapse into one file.
			Path file = directory.resolve("%s-%d%s".formatted(
					baseName.toLowerCase(Locale.ROOT), index + 1, extensionOf(image)));

			try {
				// Created on write rather than at startup, so an unusable directory surfaces as
				// an upload failure the caller can turn into 502, not as a refusal to boot.
				Files.createDirectories(directory);
				image.transferTo(file.toAbsolutePath());
			}
			catch (IOException | IllegalStateException ex) {
				throw new ImageUploadException(
						"Could not write image %d of %d to %s"
								.formatted(index + 1, images.size(), file.toAbsolutePath()),
						ex);
			}

			urls.add(file.toAbsolutePath().toUri().toString());
		}

		log.info("Stored {} image(s) for {}/{}", urls.size(), folder, baseName);
		return List.copyOf(urls);
	}

	/**
	 * Taken from the content type rather than the client's filename, which is attacker-supplied
	 * and may carry a path or no extension at all. The caller has already rejected anything that
	 * is not JPEG or PNG, so these two cases are the only ones that can arrive.
	 */
	private String extensionOf(MultipartFile image) {
		return "image/png".equalsIgnoreCase(image.getContentType()) ? ".png" : ".jpg";
	}
}
