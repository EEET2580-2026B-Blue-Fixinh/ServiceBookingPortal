package com.fixinh.sbp.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;

class CloudinaryImageStorageServiceTest {
	private Uploader uploader;
	private CloudinaryImageStorageService service;

	@BeforeEach void setUp() throws Exception {
		Cloudinary cloudinary = mock(Cloudinary.class);
		uploader = mock(Uploader.class);
		when(cloudinary.uploader()).thenReturn(uploader);
		when(uploader.upload(any(), anyMap())).thenAnswer(invocation -> Map.of("secure_url",
				"https://res.cloudinary.com/demo/image/upload/"
						+ ((Map<?, ?>) invocation.getArgument(1)).get("public_id") + ".png"));
		service = new CloudinaryImageStorageService(cloudinary);
	}

	@Test void filesEachImageIntoTheMediaLibraryFolderNamedByTheCaller() throws Exception {
		List<String> urls = service.store("cancel", "ORD-2026-1024", List.of(png("one"), png("two")));

		ArgumentCaptor<Map<String, Object>> options = optionsCaptor();
		verify(uploader, times(2)).upload(any(), options.capture());

		assertEquals("cancel", options.getAllValues().get(0).get("asset_folder"));
		assertEquals("cancel/ord-2026-1024-1", options.getAllValues().get(0).get("public_id"));
		assertEquals("cancel", options.getAllValues().get(1).get("asset_folder"));
		assertEquals("cancel/ord-2026-1024-2", options.getAllValues().get(1).get("public_id"));
		assertEquals(List.of(
				"https://res.cloudinary.com/demo/image/upload/cancel/ord-2026-1024-1.png",
				"https://res.cloudinary.com/demo/image/upload/cancel/ord-2026-1024-2.png"), urls);
	}

	@Test void keepsRatingImagesInTheirOwnFolder() throws Exception {
		service.store("ratings", "6712ab34cd56ef7890123456", List.of(png("one")));

		ArgumentCaptor<Map<String, Object>> options = optionsCaptor();
		verify(uploader).upload(any(), options.capture());

		assertEquals("ratings", options.getValue().get("asset_folder"));
		assertEquals("ratings/6712ab34cd56ef7890123456-1", options.getValue().get("public_id"));
	}

	private static MockMultipartFile png(String name) {
		return new MockMultipartFile("evidence", name + ".png", "image/png", new byte[] { 1, 2, 3 });
	}

	@SuppressWarnings("unchecked")
	private static ArgumentCaptor<Map<String, Object>> optionsCaptor() {
		return ArgumentCaptor.forClass(Map.class);
	}
}
