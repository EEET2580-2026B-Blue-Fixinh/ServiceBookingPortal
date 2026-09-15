package com.fixinh.sbp.common.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * What a caller sees when the request itself is the mistake — and, once, when it is not.
 *
 * <p>Every case here used to be a 500: the handler for {@code Exception} was consulted ahead
 * of Spring's own resolver, so the MVC exception that should have carried the status never got
 * the chance. The controller below exists only to be misused. Each of its endpoints is the
 * smallest shape that makes Spring throw one particular exception on the way in, which is what
 * makes these tests about routing and binding rather than about a mocked throw: the wrong
 * method really is rejected by the mapping, the bad parameter really does fail conversion.
 *
 * <p>Standalone MockMvc, no Spring context. The advice is the unit; nothing else in the
 * application participates in these responses.
 */
class GlobalExceptionHandlerTest {

	private static final String INTERNAL_DETAIL = "the real cause, which must never reach a client";

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	// --- Requests that never reach a controller ------------------------------

	@Test
	void aWrongMethodIs405AndSaysWhichMethodsAreAllowed() throws Exception {
		mockMvc.perform(patch("/probe/get-only"))
				.andExpect(status().isMethodNotAllowed())
				// RFC 9110 requires Allow on a 405, and it is the only part of the answer that
				// says what to send instead. The mapping declares GET alone, so that is the list.
				.andExpect(header().string("Allow", "GET"))
				.andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.path").value("/probe/get-only"))
				.andExpect(jsonPath("$.traceId").exists())
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	/**
	 * Standalone MockMvc registers no static-resource handler, so an unmatched path is
	 * rejected by the dispatcher itself rather than falling through to a resource lookup —
	 * which is the second of the two exceptions the handler accepts, and the one a deployment
	 * with resource mapping turned off would throw in production.
	 */
	@Test
	void anUnmappedPathIs404WithItsOwnCode() throws Exception {
		mockMvc.perform(get("/probe/nowhere"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("ENDPOINT_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/probe/nowhere"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void aBodyThatIsNotJsonIs400AndEchoesNothingOfIt() throws Exception {
		String body = mockMvc.perform(post("/probe/body")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"hunter2\" oops"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("REQUEST_MALFORMED"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// Jackson's own message quotes the input it was reading. On /auth/login that input is
		// a password, so the response must carry none of it -- not the value, not the token
		// that broke the parse.
		assertFalse(body.contains("hunter2"), "response repeated a value from the request body");
		assertFalse(body.contains("oops"), "response repeated the parser's view of the body");
	}

	@Test
	void aContentTypeTheEndpointDoesNotAcceptIs415() throws Exception {
		mockMvc.perform(post("/probe/body")
						.contentType(MediaType.TEXT_PLAIN)
						.content("name=x"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	// --- Parameters: VALIDATION_FAILED, with the parameter named ---------------

	@Test
	void aMissingRequiredParameterIs400NamingTheParameter() throws Exception {
		mockMvc.perform(get("/probe/required"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("q"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].cause").value("It was not sent."));
	}

	@Test
	void aMissingMultipartPartIs400NamingThePart() throws Exception {
		// A multipart request with no parts at all: the content type is right, the part the
		// endpoint requires simply is not there.
		mockMvc.perform(multipart("/probe/upload"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("evidence"))
				.andExpect(jsonPath("$.fieldErrors[0].cause").value("It was not sent."));
	}

	@Test
	void aParameterOfTheWrongTypeIs400NamingTheParameterAndNotTheValue() throws Exception {
		String body = mockMvc.perform(get("/probe/paged").param("page", "first"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
				.andExpect(jsonPath("$.fieldErrors[0].cause").value("The value sent could not be read."))
				.andReturn().getResponse().getContentAsString();

		// Neither the offending value nor the type we wanted instead. The value is theirs and
		// need not be repeated; the type describes our signature, not their mistake.
		assertFalse(body.contains("first"), "response repeated the rejected value");
		assertFalse(body.contains("Integer"), "response described the parameter's Java type");
	}

	// --- A genuine fault, still --------------------------------------------------

	/**
	 * The catch-all did not move. What it no longer receives is the traffic above; what it
	 * still receives is answered as it always was — a generic message, and a traceId that is
	 * the only bridge between this response and the stack trace in the log.
	 */
	@Test
	void aRealFaultIsStill500WithATraceIdAndNoInternalDetail() throws Exception {
		String body = mockMvc.perform(get("/probe/fault"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
				.andExpect(jsonPath("$.traceId").exists())
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertFalse(body.contains(INTERNAL_DETAIL), "response leaked the exception message");
	}

	// --- The controller that exists to be misused ----------------------------------

	@RestController
	@RequestMapping("/probe")
	static class ProbeController {

		record Payload(String name) {
		}

		@GetMapping("/get-only")
		String getOnly() {
			return "ok";
		}

		@PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
		String body(@RequestBody Payload payload) {
			return payload.name();
		}

		@GetMapping("/required")
		String required(@RequestParam String q) {
			return q;
		}

		@GetMapping("/paged")
		String paged(@RequestParam Integer page) {
			return String.valueOf(page);
		}

		@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
		String upload(@RequestPart MultipartFile evidence) {
			return evidence.getOriginalFilename();
		}

		@GetMapping("/fault")
		String fault() {
			throw new IllegalStateException(INTERNAL_DETAIL);
		}
	}
}
