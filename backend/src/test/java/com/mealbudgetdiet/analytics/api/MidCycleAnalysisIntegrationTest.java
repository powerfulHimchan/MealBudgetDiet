package com.mealbudgetdiet.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.ai.application.AiTextResult;
import com.mealbudgetdiet.ai.infrastructure.OpenAiGateway;
import com.mealbudgetdiet.analytics.infrastructure.MidCycleAnalysisRepository;
import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.shared.api.ApiException;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

@Import({ TestcontainersConfiguration.class, MidCycleAnalysisIntegrationTest.ClockConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.bootstrap-token=mid-cycle-bootstrap-token")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MidCycleAnalysisIntegrationTest {
	private static final String ENDPOINT = "/api/v1/dashboard/mid-cycle-analysis";
	private static final String OCTOBER = "{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}";
	private static final String SUMMARY = "현재까지 장보기 중심으로 소비했고 예산에 여유가 있습니다. 남은 기간에는 필요한 식재료를 미리 정하고 배달 빈도를 줄여 현재의 소비 흐름을 유지해 보세요.";
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MidCycleAnalysisRepository repository;
	@Autowired private ObjectMapper mapper;
	@Autowired private TestClock clock;
	@MockitoBean private OpenAiGateway gateway;

	@Test void cachesOncePerLedgerCycleAndHandlesTimingFailuresAndConcurrentMembers() throws Exception {
		mvc.perform(post(ENDPOINT).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(OCTOBER))
			.andExpect(status().isUnauthorized());
		Cookie owner = cookie(mvc.perform(post("/api/v1/bootstrap/admin").with(csrf()).header("X-Bootstrap-Token", "mid-cycle-bootstrap-token")
			.contentType(MediaType.APPLICATION_JSON).content("""
			{"email":"mid-owner@example.com","password":"owner-password!","displayName":"관리자","ledgerName":"장부","defaultMonthlyBudget":800000}
			""")).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Set-Cookie"));
		String code = JsonPath.read(mvc.perform(post("/api/v1/invitations").with(csrf()).cookie(owner))
			.andReturn().getResponse().getContentAsString(), "$.code");
		Cookie member = cookie(mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
			{"inviteCode":"%s","email":"mid-member@example.com","password":"member-password!","displayName":"멤버"}
			""".formatted(code))).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Set-Cookie"));
		UUID ownerLedger = ledgerId(owner);
		String category = category(owner);
		createExpense(owner, category, "2026-10-01", "MID_STORE", "MID_NOTE");
		createExpense(owner, category, "2026-10-20", "TODAY_STORE", "TODAY_NOTE");
		createExpense(owner, category, "2026-10-21", "FUTURE_STORE", "FUTURE_NOTE");
		Cookie outsider = ownLedger("outside");
		createExpense(outsider, category(outsider), "2026-10-20", "OTHER_LEDGER_STORE", "OTHER_LEDGER_NOTE");

		// Seoul is still October 15; midpoint eligibility must not use the UTC date.
		clock.set("2026-10-15T14:59:59Z");
		mvc.perform(get("/api/v1/dashboard").cookie(owner))
			.andExpect(jsonPath("$.midCycleAnalysis.eligible").value(false))
			.andExpect(jsonPath("$.midCycleAnalysis.midpoint").value("2026-10-16"));
		analyze(owner, OCTOBER).andExpect(jsonPath("$.status").value("UNAVAILABLE"));
		verifyNoInteractions(gateway);
		assertThat(jdbc.queryForObject("select count(*) from mid_cycle_analyses", Integer.class)).isZero();
		clock.set("2026-10-15T15:00:00Z");
		mvc.perform(get("/api/v1/dashboard").cookie(owner)).andExpect(jsonPath("$.midCycleAnalysis.eligible").value(true));
		verifyNoInteractions(gateway); // A dashboard GET never invokes AI.

		// First home visit is late: the result must include October 20 but exclude October 21.
		clock.set("2026-10-19T15:00:00Z");
		doAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(invocation.getArgument(0, String.class)).contains("약 100자", "상호명", "메모");
			return new AiTextResult("test-model", SUMMARY, 150, 50);
		}).when(gateway).generate(anyString(), anyString());
		mvc.perform(post(ENDPOINT).cookie(owner).contentType(MediaType.APPLICATION_JSON).content(OCTOBER)).andExpect(status().isForbidden());
		analyze(member, OCTOBER).andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.through").value("2026-10-20")).andExpect(jsonPath("$.text").value(SUMMARY));
		var prompt = ArgumentCaptor.forClass(String.class);
		verify(gateway).generate(anyString(), prompt.capture());
		assertThat(prompt.getValue()).contains("MID_STORE", "MID_NOTE", "TODAY_STORE", "TODAY_NOTE")
			.doesNotContain("FUTURE_STORE", "FUTURE_NOTE", "OTHER_LEDGER_STORE", "OTHER_LEDGER_NOTE");
		var json = mapper.readTree(prompt.getValue());
		assertThat(json.path("기간").path("to").asString()).isEqualTo("2026-10-20");
		assertThat(json.path("총지출원").asLong()).isEqualTo(20000);
		assertThat(json.path("예산원").asLong()).isEqualTo(800000);
		assertThat(json.path("현재추이마지막날예상지출원").asLong()).isEqualTo(31000);

		clock.set("2026-10-20T15:00:00Z");
		for (Cookie session : new Cookie[] { owner, member }) {
			analyze(session, OCTOBER).andExpect(jsonPath("$.text").value(SUMMARY)).andExpect(jsonPath("$.through").value("2026-10-20"));
			mvc.perform(get("/api/v1/dashboard").cookie(session)).andExpect(jsonPath("$.midCycleAnalysis.text").value(SUMMARY));
		}
		verify(gateway, times(1)).generate(anyString(), anyString());
		assertThat(jdbc.queryForObject("select count(*) from spending_analyses where ledger_id = ?", Integer.class, ownerLedger)).isZero();
		analyze(owner, "{\"from\":\"2026-09-01\",\"to\":\"2026-09-30\"}").andExpect(status().isBadRequest());
		mvc.perform(post(ENDPOINT).with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());

		Cookie empty = ownLedger("empty");
		analyze(empty, OCTOBER).andExpect(jsonPath("$.status").value("NO_EXPENSES"));
		verify(gateway, times(1)).generate(anyString(), anyString());
		assertThat(jdbc.queryForObject("select count(*) from mid_cycle_analyses where ledger_id = ?", Integer.class, ledgerId(empty))).isZero();

		// Provider failures are retryable, and two members cannot create two billed requests.
		doThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "시간 초과")).when(gateway).generate(anyString(), anyString());
		analyze(outsider, OCTOBER).andExpect(status().isGatewayTimeout());
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		doAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			entered.countDown();
			assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
			return new AiTextResult("test-model", SUMMARY, 150, 50);
		}).when(gateway).generate(anyString(), anyString());
		var executor = Executors.newSingleThreadExecutor();
		try {
			var first = executor.submit(() -> analyze(outsider, OCTOBER).andExpect(jsonPath("$.status").value("COMPLETED")));
			assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
			analyze(outsider, OCTOBER).andExpect(jsonPath("$.status").value("PROCESSING"));
			mvc.perform(get(ENDPOINT).cookie(outsider).param("from", "2026-10-01").param("to", "2026-10-31"))
				.andExpect(jsonPath("$.status").value("PROCESSING"));
			release.countDown();
			first.get(15, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
		verify(gateway, times(3)).generate(anyString(), anyString());

		// A weekly cycle uses its fourth day and the whole weekly budget.
		mvc.perform(put("/api/v1/ledger/settings/budget-cycle").with(csrf()).cookie(empty).contentType(MediaType.APPLICATION_JSON)
			.content("{\"unit\":\"WEEKLY\",\"startDay\":1,\"weekStartDay\":1,\"weeklyBudget\":100000,\"version\":0}"))
			.andExpect(status().isOk());
		String week = "{\"from\":\"2026-10-19\",\"to\":\"2026-10-25\"}";
		analyze(empty, week).andExpect(jsonPath("$.status").value("UNAVAILABLE"));
		createExpense(empty, category(empty), "2026-10-21", "WEEK_STORE", "WEEK_NOTE");
		clock.set("2026-10-21T15:00:00Z");
		analyze(empty, week).andExpect(jsonPath("$.midpoint").value("2026-10-22"))
			.andExpect(jsonPath("$.through").value("2026-10-22")).andExpect(jsonPath("$.status").value("COMPLETED"));
		analyze(empty, OCTOBER).andExpect(status().isBadRequest());
		verify(gateway, times(4)).generate(anyString(), anyString());

		// A new cycle gets an independent cache; expired requests cannot overwrite the winner.
		clock.set("2026-11-19T15:00:00Z");
		createExpense(owner, category, "2026-11-20", "NEW_CYCLE_STORE", "NEW_CYCLE_NOTE");
		var cycle = BudgetCycle.starting(YearMonth.parse("2026-11"), 1);
		UUID stale = UUID.randomUUID();
		assertThat(repository.claim(ownerLedger, cycle, LocalDate.parse("2026-11-19"), stale,
			clock.instant().minusSeconds(600), clock.instant().minusSeconds(300))).isTrue();
		analyze(owner, "{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}")
			.andExpect(jsonPath("$.through").value("2026-11-20")).andExpect(jsonPath("$.status").value("COMPLETED"));
		assertThat(repository.complete(ownerLedger, cycle, stale, "오래된 결과", new AiTextResult("old", "오래된 결과", 1, 1), clock.instant())).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from mid_cycle_analyses where ledger_id = ?", Integer.class, ownerLedger)).isEqualTo(2);
		verify(gateway, times(5)).generate(anyString(), anyString());
	}

	private org.springframework.test.web.servlet.ResultActions analyze(Cookie session, String period) throws Exception {
		return mvc.perform(post(ENDPOINT).with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(period));
	}
	private Cookie ownLedger(String name) throws Exception {
		return cookie(mvc.perform(post("/api/v1/auth/register-ledger").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
			{"email":"mid-%s@example.com","password":"outside-password!","displayName":"멤버","ledgerName":"별도 장부","defaultMonthlyBudget":800000}
			""".formatted(name))).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Set-Cookie"));
	}
	private UUID ledgerId(Cookie session) throws Exception {
		return UUID.fromString(JsonPath.read(mvc.perform(get("/api/v1/ledger").cookie(session)).andReturn().getResponse().getContentAsString(), "$.id"));
	}
	private String category(Cookie session) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/v1/categories").cookie(session)).andReturn().getResponse().getContentAsString(), "$.items[0].id");
	}
	private void createExpense(Cookie session, String category, String date, String merchant, String memo) throws Exception {
		mvc.perform(post("/api/v1/expenses").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
			{"amount":10000,"spentOn":"%s","categoryId":"%s","merchant":"%s","memo":"%s","imageIds":[]}
			""".formatted(date, category, merchant, memo))).andExpect(status().isCreated());
	}
	private static Cookie cookie(String header) {
		return new Cookie("MBD_SESSION", header.substring(header.indexOf("MBD_SESSION=") + 12).split(";", 2)[0]);
	}
	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfig {
		@Bean @Primary TestClock midCycleClock() { return new TestClock(new AtomicReference<>(Instant.parse("2026-10-15T14:59:59Z")), ZoneOffset.UTC); }
	}
	static class TestClock extends Clock {
		private final AtomicReference<Instant> value;
		private final ZoneId zone;
		TestClock(AtomicReference<Instant> value, ZoneId zone) { this.value = value; this.zone = zone; }
		void set(String instant) { value.set(Instant.parse(instant)); }
		@Override public ZoneId getZone() { return zone; }
		@Override public Clock withZone(ZoneId zone) { return new TestClock(value, zone); }
		@Override public Instant instant() { return value.get(); }
	}
}
