package com.fixinh.sbp.common.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Relays uploaded images to Cloudinary and keeps only the URLs (Reqs 3.3.M1, 3.4.M1).
 *
 * <p>Chosen when {@code fixinh.sbp.images.mode} is {@code cloudinary}, never by default.
 *
 * <p><b>Through the backend, not from the browser.</b> §2.6 and §7.4 both require it: the account
 * credential stays on the server, and the bytes are checked for size and type before anything
 * leaves the machine. A signed direct-to-Cloudinary upload would move both of those out of SBP's
 * reach.
 */
@Component
@ConditionalOnProperty(prefix = "fixinh.sbp.images", name = "mode", havingValue = "cloudinary")
@Slf4j
public class CloudinaryImageStorageService implements ImageStorageService {

	private final Cloudinary cloudinary;

	public CloudinaryImageStorageService(Cloudinary cloudinary) {
		this.cloudinary = cloudinary;
	}

	/** Which implementation is running should be answerable from the startup log alone. */
	@PostConstruct
	void announceMode() {
		log.info("Image storage: CLOUDINARY mode. Uploaded images are published to the "
				+ "configured account.");
	}

	@Override
	public List<String> store(String folder, String baseName, List<MultipartFile> images) {
		if (images == null || images.isEmpty()) {
			return List.of();
		}

		List<String> urls = new ArrayList<>(images.size());

		for (int index = 0; index < images.size(); index++) {
			// Numbered from one, and folder-scoped, so the URL reads the way §7.4's example does.
			// Cloudinary derives the extension itself, so publicId carries none.
			String publicId = "%s/%s-%d".formatted(
					folder, baseName.toLowerCase(Locale.ROOT), index + 1);

			urls.add(upload(images.get(index), publicId, index + 1, images.size()));
		}

		log.info("Uploaded {} image(s) for {}/{}", urls.size(), folder, baseName);
		return List.copyOf(urls);
	}

	private String upload(MultipartFile image, String publicId, int position, int total) {
		try {
			Map<?, ?> result = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.asMap(
					"public_id", publicId,
					"resource_type", "image",
					// Re-uploading the same evidence replaces it rather than accumulating
					// near-duplicates under generated names.
					"overwrite", true));

			Object secureUrl = result.get("secure_url");
			if (secureUrl == null) {
				throw new ImageUploadException(
						"Cloudinary accepted image " + position + " but returned no secure_url.",
						null);
			}
			return secureUrl.toString();
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ImageUploadException alreadyWrapped) {
				throw alreadyWrapped;
			}
			throw new ImageUploadException(
					"Cloudinary refused image %d of %d.".formatted(position, total), ex);
		}
	}
}
