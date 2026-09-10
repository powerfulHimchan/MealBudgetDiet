package com.mealbudgetdiet.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.TestcontainersConfiguration.TestPasswordResetEmailSender;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
	"app.bootstrap-token=test-auth-security-bootstrap-token",
	"app.auth-security.rate-limit-secret=test-auth-rate-limit-secret-with-at-least-32-characters",
	"app.auth-security.login.max-failures=2",
	"app.auth-security.login.window=15m",
	"app.auth-security.password-reset.max-requests-per-email=1",
	"app.auth-security.password-reset.max-requests-per-client=10",
	"app.auth-security.password-reset.window=1h"
})
class AuthSecurityIntegrationTest {

	private static final String OWNER_EMAIL = "security-owner@example.com";
	private static final String CLIENT_ADDRESS = "203.0.113.10";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TestPasswordResetEmailSender passwordResetEmailSender;

	@BeforeEach
	void setUp() throws Exception {
		if (jdbcTemplate.queryForObject("select count(*) from users", Integer.class) == 0) {
			mockMvc.perform(post("/api/v1/bootstrap/admin")
					.with(csrf())
					.header("X-Bootstrap-Token", "test-auth-security-bootstrap-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "email":"security-owner@example.com",
						  "password":"password123!",
						  "displayName":"보안 관리자",
						  "ledgerName":"보안 테스트 장부",
						  "defaultMonthlyBudget":800000
						}
						"""))
				.andExpect(status().isCreated());
		}
		jdbcTemplate.update("delete from auth_rate_limits");
		passwordResetEmailSender.clear();
	}

	@Test
	void limitsRepeatedLoginFailuresAndClearsTheLimitAfterSuccess(CapturedOutput output) throws Exception {
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(login("wrong-password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		}

		String subjectHash = jdbcTemplate.queryForObject(
			"select subject_hash from auth_rate_limits where scope = 'LOGIN_FAILURE'",
			String.class
		);
		assertThat(subjectHash).matches("[0-9a-f]{64}").doesNotContain(OWNER_EMAIL);

		mockMvc.perform(login("wrong-password"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
		mockMvc.perform(login("password123!"))
			.andExpect(status().isTooManyRequests());

		jdbcTemplate.update("update auth_rate_limits set window_started_at = '2000-01-01T00:00:00Z'");
		mockMvc.perform(login("password123!"))
			.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from auth_rate_limits where scope = 'LOGIN_FAILURE'",
			Integer.class
		)).isZero();

		assertThat(output)
			.contains("login")
			.contains("rate_limited")
			.doesNotContain(OWNER_EMAIL)
			.doesNotContain("wrong-password");
	}

	@Test
	void silentlyLimitsPasswordResetRequestsByEmail(CapturedOutput output) throws Exception {
		mockMvc.perform(passwordResetRequest(OWNER_EMAIL, CLIENT_ADDRESS))
			.andExpect(status().isAccepted());
		assertThat(passwordResetEmailSender.size()).isEqualTo(1);

		mockMvc.perform(passwordResetRequest(OWNER_EMAIL, CLIENT_ADDRESS))
			.andExpect(status().isAccepted());
		mockMvc.perform(passwordResetRequest(OWNER_EMAIL, "203.0.113.11"))
			.andExpect(status().isAccepted());
		assertThat(passwordResetEmailSender.size()).isEqualTo(1);

		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from auth_rate_limits where scope = 'PASSWORD_RESET_EMAIL'",
			Integer.class
		)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select attempt_count from auth_rate_limits where scope = 'PASSWORD_RESET_EMAIL'",
			Integer.class
		)).isEqualTo(2);
		assertThat(output)
			.contains("password_reset_requested")
			.contains("rate_limited")
			.doesNotContain(OWNER_EMAIL);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String password) {
		return post("/api/v1/auth/login")
			.with(csrf())
			.with(remoteAddress(CLIENT_ADDRESS))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s","password":"%s"}
				""".formatted(OWNER_EMAIL, password));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder passwordResetRequest(
		String email,
		String clientAddress
	) {
		return post("/api/v1/auth/password-reset-requests")
			.with(csrf())
			.with(remoteAddress(clientAddress))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s"}
				""".formatted(email));
	}

	private RequestPostProcessor remoteAddress(String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}
}
