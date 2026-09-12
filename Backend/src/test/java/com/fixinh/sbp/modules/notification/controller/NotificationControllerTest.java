package com.fixinh.sbp.modules.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fixinh.sbp.common.dto.PageResponse;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.exception.GlobalExceptionHandler;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.notification.dto.external.NotificationDto;
import com.fixinh.sbp.modules.notification.dto.external.NotificationListDto;
import com.fixinh.sbp.modules.notification.interfaces.external.NotificationExternalService;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * The three endpoints of §9.1 and §9.2 over HTTP, with the service mocked.
 *
 * <p>Everything below this line already has tests; nothing above it did. What is checked here
 * is the part only a real request exercises — the paths, the query-parameter binding, the
 * caller arriving from the token rather than from a parameter, the status codes, and an
 * {@link ApiException} reaching the client as §2.7's body.
 *
 * <p>Standalone rather than a Spring slice, deliberately. A slice would load
 * {@code SecurityConfig} and every filter it depends on to test a controller that delegates
 * each of its three methods in one line. The two pieces of infrastructure that genuinely
 * participate in a response are wired in by hand instead: the resolver behind
 * {@code @AuthenticationPrincipal}, and the advice that renders failures. Authorisation
 * itself is {@code SecurityConfig}'s and is not in scope here — these tests assume a request
 * that already got past it.
 */
class NotificationControllerTest {

	private static final String BASE = "/api/v1/notifications";
	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final String NOTIFICATION_ID = "6713cd45ef67ab8901234567";

	private NotificationExternalService notificationService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		notificationService = mock(NotificationExternalService.class);

		mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		// An empty page by default, so a test about parameters is not also a test about what
		// comes back. The one test that cares about the body stubs over this.
		when(notificationService.list(anyString(), any(), any(), any(), any()))
				.thenReturn(NotificationListDto.empty(0, 20));

		signIn(CUSTOMER_ID, Role.CUSTOMER);
	}

	@AfterEach
	void tearDown() {
		// Static state, so leaving it set would hand the next test a caller it never asked for.
		SecurityContextHolder.clearContext();
	}

	@Test
	void listReturnsTheBodyOfSection91() throws Exception {
		when(notificationService.list(anyString(), any(), any(), any(), any()))
				.thenReturn(new NotificationListDto(
						List.of(scheduled(), confirmed()), 3L, PageResponse.Metadata.of(0, 20, 2L)));

		mockMvc.perform(get(BASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].notificationId").value(NOTIFICATION_ID))
				.andExpect(jsonPath("$.items[0].type").value("BOOKING_SCHEDULED"))
				.andExpect(jsonPath("$.items[0].message").exists())
				.andExpect(jsonPath("$.items[0].bookingRef").value("ORD-2026-1024"))
				.andExpect(jsonPath("$.items[0].readStatus").value(false))
				.andExpect(jsonPath("$.items[0].createdAt").exists())
				// The badge count, and it is the caller's total rather than this page's.
				.andExpect(jsonPath("$.unreadCount").value(3))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.totalElements").value(2))
				.andExpect(jsonPath("$.page.totalPages").value(1));
	}

	/**
	 * §9.1 says {@code bookingRef} is present whenever the notification concerns a booking,
	 * which reads as absent otherwise — not present and null. The type used here is the only
	 * one that never names a booking; a customer would not normally have one, and that is
	 * beside the point, because what is being checked is how the field is rendered when it is
	 * missing.
	 */
	@Test
	void aNotificationAboutNoBookingOmitsBookingRefRatherThanNullingIt() throws Exception {
		when(notificationService.list(anyString(), any(), any(), any(), any()))
				.thenReturn(new NotificationListDto(
						List.of(new NotificationDto("6713cd45ef67ab8901234569",
								NotificationType.STAFF_ACCOUNT_CREATED, "Welcome email sent.", null,
								true, Instant.parse("2026-08-11T03:25:00Z"))),
						0L, PageResponse.Metadata.of(0, 20, 1L)));

		mockMvc.perform(get(BASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].bookingRef").doesNotExist());
	}

	/**
	 * Nothing supplied stays nothing supplied all the way into the service, which is where
	 * §2.6's defaults live. A {@code defaultValue} on the controller would answer the same way
	 * today and put the same rule in two places.
	 */
	@Test
	void listLeavesTheDefaultsToTheServiceWhenNothingIsAsked() throws Exception {
		mockMvc.perform(get(BASE)).andExpect(status().isOk());

		verify(notificationService).list(eq(CUSTOMER_ID), eq(Role.CUSTOMER), isNull(), isNull(),
				isNull());
	}

	@Test
	void listPassesEveryQueryParameterThrough() throws Exception {
		mockMvc.perform(get(BASE).param("unreadOnly", "true").param("page", "2").param("size", "5"))
				.andExpect(status().isOk());

		verify(notificationService).list(CUSTOMER_ID, Role.CUSTOMER, true, 2, 5);
	}

	/**
	 * Req A.2.a: the recipient is the {@code sub} claim. A parameter naming somebody else is
	 * not rejected, because there is nothing to reject — no endpoint here reads one.
	 */
	@Test
	void listIgnoresARecipientNamedInTheQueryString() throws Exception {
		mockMvc.perform(get(BASE).param("recipientId", "66f1a2b3c4d5e6f708192a3c"))
				.andExpect(status().isOk());

		verify(notificationService).list(eq(CUSTOMER_ID), eq(Role.CUSTOMER), isNull(), isNull(),
				isNull());
	}

	/**
	 * An administrator reaches these endpoints too (§2.4 gives them the group, own only), and
	 * the controller hands their role on rather than deciding anything about it.
	 */
	@Test
	void listActsOnWhoeverTheTokenNames() throws Exception {
		signIn("66f1a2b3c4d5e6f708192a99", Role.ADMIN);

		mockMvc.perform(get(BASE)).andExpect(status().isOk());

		verify(notificationService).list(eq("66f1a2b3c4d5e6f708192a99"), eq(Role.ADMIN), isNull(),
				isNull(), isNull());
	}

	@Test
	void markReadAnswers204() throws Exception {
		mockMvc.perform(patch(BASE + "/" + NOTIFICATION_ID + "/read"))
				.andExpect(status().isNoContent());

		verify(notificationService).markRead(CUSTOMER_ID, Role.CUSTOMER, NOTIFICATION_ID);
	}

	/** §9.2 is idempotent, and the frontend marks on open — an open can happen twice. */
	@Test
	void markingTheSameNotificationTwiceAnswers204Both() throws Exception {
		mockMvc.perform(patch(BASE + "/" + NOTIFICATION_ID + "/read"))
				.andExpect(status().isNoContent());
		mockMvc.perform(patch(BASE + "/" + NOTIFICATION_ID + "/read"))
				.andExpect(status().isNoContent());

		verify(notificationService, times(2))
				.markRead(CUSTOMER_ID, Role.CUSTOMER, NOTIFICATION_ID);
	}

	/**
	 * Unknown and somebody else's are the same answer (§9.2), and it is a 404 rather than the
	 * 403 §2.4 gives elsewhere, so an id cannot be probed for existence. The body says only
	 * which error code it is.
	 */
	@Test
	void markReadAnswers404InTheStandardErrorBody() throws Exception {
		doThrow(new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND))
				.when(notificationService).markRead(anyString(), any(), anyString());

		mockMvc.perform(patch(BASE + "/6713cd45ef67ab8901234500/read"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOTIFICATION_NOT_FOUND"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value(BASE + "/6713cd45ef67ab8901234500/read"))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.traceId").exists())
				// Not a validation failure, so the field-level array is left out entirely.
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void markAllReadAnswers204() throws Exception {
		mockMvc.perform(patch(BASE + "/read-all")).andExpect(status().isNoContent());

		verify(notificationService).markAllRead(CUSTOMER_ID, Role.CUSTOMER);
	}

	/**
	 * The two PATCH paths differ by a segment, so nothing in the mapping makes them ambiguous
	 * — but "read-all" is exactly the kind of literal a later {@code /{notificationId}} route
	 * would swallow, and it would swallow it silently.
	 */
	@Test
	void markAllReadIsNotRoutedAsANotificationIdentifier() throws Exception {
		mockMvc.perform(patch(BASE + "/read-all")).andExpect(status().isNoContent());

		verify(notificationService, never()).markRead(any(), any(), any());
	}

	// --- Fixtures ------------------------------------------------------------

	private static NotificationDto scheduled() {
		return new NotificationDto(NOTIFICATION_ID, NotificationType.BOOKING_SCHEDULED,
				"Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
				"ORD-2026-1024", false, Instant.parse("2026-08-11T03:20:00Z"));
	}

	private static NotificationDto confirmed() {
		return new NotificationDto("6713cd45ef67ab8901234568", NotificationType.BOOKING_CONFIRMED,
				"Your order ORD-2026-1025 has been confirmed.", "ORD-2026-1025", true,
				Instant.parse("2026-08-10T09:41:00Z"));
	}

	/**
	 * Puts a caller in the security context, which is where the resolver behind
	 * {@code @AuthenticationPrincipal} reads from — the same route a request takes once
	 * {@code JwtAuthenticationFilter} has parsed its token.
	 */
	private static void signIn(String id, Role role) {
		UserPrincipal principal = new UserPrincipal(id, "caller@fixinh.vn", role, "jti-1",
				Instant.now().plusSeconds(600));

		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
	}
}
