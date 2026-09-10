package com.mealbudgetdiet.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"app.bootstrap-token=ledger-bootstrap-token",
	"app.public-base-url=https://budget.example"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LedgerCollaborationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private static Cookie adminSession;
	private static Cookie memberSession;
	private static UUID adminId;
	private static UUID memberId;
	private static UUID invitationId;
	private static String invitationCode;

	@Test
	@Order(1)
	void createsLedgerOwner() throws Exception {
		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "ledger-bootstrap-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"owner-ledger@example.com",
					  "password":"owner-password!",
					  "displayName":"장부 관리자",
					  "ledgerName":"우리집 식비",
					  "defaultMonthlyBudget":800000
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();

		adminSession = sessionCookie(result.getResponse().getHeader("Set-Cookie"));
		adminId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	@Test
	@Order(2)
	void readsCurrentLedgerAndMembers() throws Exception {
		mockMvc.perform(get("/api/v1/ledger").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("우리집 식비"))
			.andExpect(jsonPath("$.defaultMonthlyBudget").value(800000))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.currentUserRole").value("ADMIN"));

		mockMvc.perform(get("/api/v1/members").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].displayName").value("장부 관리자"));
	}

	@Test
	@Order(3)
	void issuesInvitationWithoutStoringRawCodeInList() throws Exception {
		var result = mockMvc.perform(post("/api/v1/invitations")
				.with(csrf())
				.cookie(adminSession))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("MBD-")))
			.andExpect(jsonPath("$.joinUrl").value(org.hamcrest.Matchers.startsWith("https://budget.example/join?code=MBD-")))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		invitationId = UUID.fromString(JsonPath.read(body, "$.id"));
		invitationCode = JsonPath.read(body, "$.code");

		mockMvc.perform(get("/api/v1/invitations").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].maskedCode").value(org.hamcrest.Matchers.startsWith("MBD-****")))
			.andExpect(jsonPath("$.items[0].maskedCode").value(org.hamcrest.Matchers.not(invitationCode)));
	}

	@Test
	@Order(4)
	void joinsTheSameLedgerWithInvitation() throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode":"%s",
					  "email":"member-ledger@example.com",
					  "password":"member-password!",
					  "displayName":"가족 구성원"
					}
					""".formatted(invitationCode)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andReturn();

		memberSession = sessionCookie(result.getResponse().getHeader("Set-Cookie"));
		memberId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

		mockMvc.perform(get("/api/v1/ledger").cookie(memberSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberCount").value(2))
			.andExpect(jsonPath("$.currentUserRole").value("MEMBER"));
	}

	@Test
	@Order(5)
	void letsEveryMemberIssueInvitationsButOnlyAdminsChangeRoles() throws Exception {
		mockMvc.perform(post("/api/v1/invitations").with(csrf()).cookie(memberSession))
			.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/v1/members/{memberId}/role", adminId)
				.with(csrf())
				.cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"MEMBER\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		mockMvc.perform(patch("/api/v1/members/{memberId}/role", memberId)
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"ADMIN\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("ADMIN"));

		mockMvc.perform(patch("/api/v1/members/{memberId}/role", memberId)
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"MEMBER\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/members/{memberId}/role", adminId)
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"MEMBER\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
	}

	@Test
	@Order(6)
	void revokesReusableInvitationIdempotently() throws Exception {
		mockMvc.perform(delete("/api/v1/invitations/{invitationId}", invitationId)
				.with(csrf())
				.cookie(memberSession))
			.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/invitations/{invitationId}", invitationId)
				.with(csrf())
				.cookie(memberSession))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/invitations").param("status", "REVOKED").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].status").value("REVOKED"));

		mockMvc.perform(post("/api/v1/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode":"%s",
					  "email":"rejected@example.com",
					  "password":"password123!",
					  "displayName":"거절 대상"
					}
					""".formatted(invitationCode)))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("INVITATION_REVOKED"));
	}

	@Test
	@Order(7)
	void withdrawsMemberAndInvalidatesTheirSession() throws Exception {
		var result = mockMvc.perform(post("/api/v1/account/withdrawal")
				.with(csrf())
				.cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"member-password!\"}"))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(result.getResponse().getHeader("Set-Cookie"))
			.isNotNull()
			.contains("MBD_SESSION=")
			.contains("Max-Age=0");

		mockMvc.perform(get("/api/v1/auth/me").cookie(memberSession))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/members").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	@Order(8)
	void requiresExactConfirmationBeforeLastAdminDeletesLedger() throws Exception {
		mockMvc.perform(post("/api/v1/account/withdrawal")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"owner-password!","confirmation":"잘못된 확인"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LEDGER_DELETION_CONFIRMATION_REQUIRED"));

		mockMvc.perform(post("/api/v1/account/withdrawal")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"owner-password!","confirmation":"우리집 식비 삭제"}
					"""))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/bootstrap/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true));
	}

	private Cookie sessionCookie(String setCookieHeader) {
		assertThat(setCookieHeader).isNotNull().contains("MBD_SESSION=");
		String prefix = "MBD_SESSION=";
		int valueStart = setCookieHeader.indexOf(prefix) + prefix.length();
		int valueEnd = setCookieHeader.indexOf(';', valueStart);
		return new Cookie("MBD_SESSION", setCookieHeader.substring(valueStart, valueEnd));
	}
}
