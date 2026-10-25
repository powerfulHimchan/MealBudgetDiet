package com.mealbudgetdiet.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.identity.application.WidgetConnectionService;
import com.mealbudgetdiet.ledger.application.OnboardingService;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.bootstrap-token=widget-bootstrap-token")
class WidgetConnectionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OnboardingService onboarding;

    @BeforeEach void prepareServiceAdmin() {
        if (onboarding.isBootstrapAvailable()) onboarding.bootstrapAdmin("widget-bootstrap-token",
            "widget-bootstrap@example.com", "widget-password!", "위젯 테스트 관리자", "관리자 장부", 800000);
    }

    @Test void capabilityIsSingleUseReadOnlySessionBoundAndRevocable() throws Exception {
        Cookie session = register("widget-owner");
        String verifier = "a".repeat(43);
        String challenge = WidgetConnectionService.hash(verifier);
        mvc.perform(post("/api/v1/widget/connections").contentType(MediaType.APPLICATION_JSON)
            .content("{\"challenge\":\"" + challenge + "\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/widget/connections").cookie(session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"challenge\":\"" + challenge + "\"}")).andExpect(status().isForbidden());
        authorize(session, challenge);
        claim("b".repeat(43), 401);
        String token = claim(verifier, 200);
        claim(verifier, 401);
        mvc.perform(get("/api/v1/widget/device/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.todaySpent").value(0)).andExpect(jsonPath("$.remaining").value(800000))
            .andExpect(jsonPath("$.recentExpenses").doesNotExist());
        // The token never authenticates normal application endpoints, even with valid CSRF.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/expenses").with(csrf()).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(authenticated(delete("/api/v1/widget/connections"), session)).andExpect(status().isNoContent());
        summaryUnauthorized(token);

        String second = "c".repeat(43);
        authorize(session, WidgetConnectionService.hash(second));
        String secondToken = claim(second, 200);
        String cookieSessionId = new String(java.util.Base64.getDecoder().decode(session.getValue()), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(jdbc.queryForObject("select session_id from widget_connections where token_hash = ?", String.class,
            WidgetConnectionService.hash(secondToken))).as("grant must bind the original cookie session").isEqualTo(cookieSessionId);
        mvc.perform(authenticated(post("/api/v1/auth/logout"), session)).andExpect(status().isNoContent())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler().handlerType(AuthController.class));
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class,
            cookieSessionId)).as("logout must delete the approving session").isZero();
        assertThat(jdbc.queryForObject("select count(*) from widget_connections where token_hash = ?", Integer.class,
            WidgetConnectionService.hash(secondToken))).as("session deletion must cascade to widget capabilities").isZero();
        summaryUnauthorized(secondToken);
        assertThat(jdbc.queryForObject("select count(*) from widget_connections where token_hash = ?", Integer.class,
            WidgetConnectionService.hash(secondToken))).isZero();
    }

    @Test void expiredGrantsSessionsAndMembershipsCannotRevealAmounts() throws Exception {
        Cookie session = register("widget-expiry");
        String verifier = "d".repeat(43), challenge = WidgetConnectionService.hash(verifier);
        authorize(session, challenge);
        jdbc.update("update widget_connections set claim_until = current_timestamp - interval '1 second' where challenge = ?", challenge);
        claim(verifier, 401);
        verifier = "e".repeat(43); authorize(session, WidgetConnectionService.hash(verifier));
        String token = claim(verifier, 200);
        jdbc.update("update widget_connections set expires_at = current_timestamp - interval '1 second' where token_hash = ?", WidgetConnectionService.hash(token));
        summaryUnauthorized(token);
        verifier = "f".repeat(43); authorize(session, WidgetConnectionService.hash(verifier));
        token = claim(verifier, 200);
        jdbc.update("update ledger_members set status = 'LEFT' where user_id = (select user_id from widget_connections where token_hash = ?)", WidgetConnectionService.hash(token));
        summaryUnauthorized(token);
        jdbc.update("update ledger_members set status = 'ACTIVE' where user_id = (select user_id from widget_connections where token_hash = ?)", WidgetConnectionService.hash(token));
        mvc.perform(get("/api/v1/widget/device/summary").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        jdbc.update("update spring_session set expiry_time = 0 where session_id = (select session_id from widget_connections where token_hash = ?)", WidgetConnectionService.hash(token));
        summaryUnauthorized(token);
        mvc.perform(get("/api/v1/widget/device/summary")).andExpect(status().isUnauthorized());
    }

    private Cookie register(String name) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register-ledger").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"%s@example.com","password":"widget-password!","displayName":"위젯 테스트","ledgerName":"위젯 장부","defaultMonthlyBudget":800000}
            """.formatted(name))).andExpect(status().isCreated()).andReturn().getResponse();
        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("MBD_SESSION=");
        return new Cookie("MBD_SESSION", header.split(";", 2)[0].substring("MBD_SESSION=".length()));
    }
    private void authorize(Cookie session, String challenge) throws Exception {
        mvc.perform(authenticated(post("/api/v1/widget/connections"), session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"challenge\":\"" + challenge + "\"}")).andExpect(status().isNoContent());
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Cookie session) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.token");
        String header = JsonPath.read(response.getContentAsString(), "$.headerName");
        return request.cookie(session, new Cookie("XSRF-TOKEN", token)).header(header, token);
    }
    private String claim(String verifier, int status) throws Exception {
        var response = mvc.perform(post("/api/v1/widget/device/claim").contentType(MediaType.APPLICATION_JSON)
            .content("{\"verifier\":\"" + verifier + "\"}")).andExpect(status().is(status)).andReturn().getResponse();
        return status == 200 ? JsonPath.read(response.getContentAsString(), "$.token") : null;
    }
    private void summaryUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/v1/widget/device/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}
