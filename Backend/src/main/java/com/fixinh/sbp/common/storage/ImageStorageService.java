package com.fixinh.sbp.common.storage;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * Where an uploaded image ends up, and the URL that is kept instead of the bytes (Reqs 3.3.M1,
 * 3.4.M1; API Documentation §7.4, §8.1).
 *
 * <p>Two implementations, chosen by {@code fixinh.sbp.images.mode} and never by the caller, the
 * same arrangement {@code EmailClient} uses:
 * <ul>
 *   <li>{@code local} — writes under {@code target/uploads} and returns {@code file:} URLs. The
 *       default, so the cancellation and rating flows run end to end with no Cloudinary account.
 *       The URLs are real paths on disk but are not browser-renderable, exactly as a message
 *       written by the {@code file} email client is real but not delivered.</li>
 *   <li>{@code cloudinary} — uploads to Cloudinary and returns the public URLs the API
 *       documentation shows.</li>
 * </ul>
 *
 * <p><b>Only URLs travel onward.</b> Image bytes never reach Mongo and never cross the SBP/SMP
 * boundary (§7.4's own note); SMP is handed the links and nothing else.
 */
public interface ImageStorageService {

	/**
	 * Stores every image and returns their URLs, in the order given.
	 *
	 * @param folder   the logical group the images belong to, e.g. {@code cancel} or
	 *                 {@code ratings} — it becomes a path segment of the URL
	 * @param baseName the stem each file is named after, e.g. a booking reference. Files are
	 *                 numbered from one, giving {@code cancel/ord-2026-1024-1.jpg}, matching the
	 *                 shape §7.4 publishes
	 * @param images   may be empty, in which case an empty list comes back and nothing is stored
	 * @throws ImageUploadException if any image could not be stored. Nothing partial is left
	 *         behind for the caller to reason about: the caller aborts and records no change,
	 *         which is what §7.4's 502 UPLOAD_FAILED promises
	 */
	List<String> store(String folder, String baseName, List<MultipartFile> images);
}
