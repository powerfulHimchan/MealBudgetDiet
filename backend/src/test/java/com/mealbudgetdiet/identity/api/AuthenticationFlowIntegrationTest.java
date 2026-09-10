package com.mealbudgetdiet.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.shared.security.TokenHasher;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.bootstrap-token=test-bootstrap-token")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TokenHasher tokenHasher;

	private static Cookie adminSession;

	@Test
	@Order(1)
	void bootstrapsFirstAdminAndCreatesDefaultCategories() throws Exception {
		mockMvc.perform(get("/api/v1/bootstrap/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true));

		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "test-bootstrap-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "Owner@Example.com ",
					  "password": "password123!",
					  "displayName": "힘찬",
					  "ledgerName": "우리집 식비",
					  "defaultMonthlyBudget": 800000
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("owner@example.com"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andReturn();

		String setCookie = result.getResponse().getHeader("Set-Cookie");
		assertThat(setCookie)
			.isNotNull()
			.contains("MBD_SESSION=")
			.contains("Max-Age=" + Integer.MAX_VALUE)
			.contains("HttpOnly");
		adminSession = sessionCookie(setCookie);
		assertThat(jdbcTemplate.queryForObject(
			"select max_inactive_interval from spring_session", Integer.class)).isEqualTo(-1);
		assertThat(jdbcTemplate.queryForObject(
			"select expiry_time from spring_session", Long.class)).isEqualTo(Long.MAX_VALUE);
		assertThat(jdbcTemplate.queryForObject("select count(*) from categories", Integer.class)).isEqualTo(5);
		assertThat(jdbcTemplate.queryForObject("select password_hash from users", String.class))
			.startsWith("{bcrypt}")
			.doesNotContain("password123!");
	}

	@Test
	@Order(2)
	void restoresAuthenticatedUserFromJdbcSession() throws Exception {
		jdbcTemplate.update("update spring_session set last_access_time = 0");

		var result = mockMvc.perform(get("/api/v1/auth/me").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("힘찬"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andReturn();

		assertThat(result.getResponse().getHeader("Set-Cookie"))
			.contains("MBD_SESSION=")
			.contains("Max-Age=" + Integer.MAX_VALUE);
		assertThat(jdbcTemplate.queryForObject(
			"select last_access_time from spring_session", Long.class)).isPositive();
	}

	@Test
	@Order(3)
	void registersMemberOnlyWithAValidInvitation() throws Exception {
		UUID ledgerId = jdbcTemplate.queryForObject("select id from ledgers", UUID.class);
		UUID ownerId = jdbcTemplate.queryForObject("select id from users where email = 'owner@example.com'", UUID.class);
		String rawCode = "MBD-TEST-7K2P";
		jdbcTemplate.update("""
			insert into invitations (id, ledger_id, created_by_user_id, token_hash, code_suffix)
			values (?, ?, ?, ?, ?)
			""", UUID.randomUUID(), ledgerId, ownerId, tokenHasher.hash(rawCode), "7K2P");

		mockMvc.perform(post("/api/v1/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "mbd-test-7k2p",
					  "email": "member@example.com",
					  "password": "password456!",
					  "displayName": "가족"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("member@example.com"))
			.andExpect(jsonPath("$.role").value("MEMBER"));

		assertThat(jdbcTemplate.queryForObject("select use_count from invitations", Long.class)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject("select count(*) from invitations where token_hash = ?", Integer.class, rawCode))
			.isZero();
	}

	@Test
	@Order(4)
	void rejectsInvalidCredentialsWithoutRevealingAccountExistence() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"owner@example.com","password":"wrong-password"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"missing@example.com","password":"wrong-password"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	@Order(5)
	void changesPasswordAndInvalidatesEverySession() throws Exception {
		var secondLogin = mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"owner@example.com","password":"password123!"}
					"""))
			.andExpect(status().isOk())
			.andReturn();
		Cookie secondAdminSession = sessionCookie(secondLogin.getResponse().getHeader("Set-Cookie"));

		mockMvc.perform(post("/api/v1/account/password-change")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"wrong-password","newPassword":"new-password123!"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		var result = mockMvc.perform(post("/api/v1/account/password-change")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"password123!","newPassword":"new-password123!"}
					"""))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(result.getResponse().getHeader("Set-Cookie"))
			.contains("MBD_SESSION=")
			.contains("Max-Age=0");
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from spring_session where principal_name = ?",
			Integer.class,
			"owner@example.com"
		)).isZero();
		mockMvc.perform(get("/api/v1/auth/me").cookie(adminSession))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/auth/me").cookie(secondAdminSession))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"owner@example.com","password":"password123!"}
					"""))
			.andExpect(status().isUnauthorized());

		var relogin = mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"owner@example.com","password":"new-password123!"}
					"""))
			.andExpect(status().isOk())
			.andReturn();
		adminSession = sessionCookie(relogin.getResponse().getHeader("Set-Cookie"));
	}

	@Test
	@Order(6)
	void logsOutAndInvalidatesCurrentSession() throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(adminSession))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(result.getResponse().getHeader("Set-Cookie"))
			.contains("MBD_SESSION=")
			.contains("Max-Age=0");
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from spring_session where principal_name = ?",
			Integer.class,
			"owner@example.com"
		)).isZero();

		mockMvc.perform(get("/api/v1/auth/me").cookie(adminSession))
			.andExpect(status().isUnauthorized());
	}

	private Cookie sessionCookie(String setCookieHeader) {
		String prefix = "MBD_SESSION=";
		int valueStart = setCookieHeader.indexOf(prefix) + prefix.length();
		int valueEnd = setCookieHeader.indexOf(';', valueStart);
		return new Cookie("MBD_SESSION", setCookieHeader.substring(valueStart, valueEnd));
	}
}
