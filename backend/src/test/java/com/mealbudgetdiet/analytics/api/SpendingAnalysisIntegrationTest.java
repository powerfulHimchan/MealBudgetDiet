package com.mealbudgetdiet.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.ai.application.AiTextResult;
import com.mealbudgetdiet.ai.infrastructure.OpenAiGateway;
import com.mealbudgetdiet.analytics.infrastructure.SpendingAnalysisRepository;
import com.mealbudgetdiet.shared.api.ApiException;
import jakarta.servlet.http.Cookie;

@Import({ TestcontainersConfiguration.class, SpendingAnalysisIntegrationTest.FixedClockConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.bootstrap-token=spending-analysis-bootstrap-token")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SpendingAnalysisIntegrationTest {
	private static final String SUMMARY = "이번 주기는 장보기 중심으로 소비했으며 예산 사용률은 낮지만 이전 주기보다 총지출이 늘었습니다. 지출이 가장 많았던 날과 상위 카테고리의 비중을 함께 살펴보면 다음 주기에서 점검할 항목을 정하는 데 도움이 됩니다. 다만 집계 정보만으로는 지출이 늘어난 원인이나 개별 구매의 필요성을 판단하기 어렵습니다. 다음 주기에는 필요한 품목과 구매 순서를 미리 정하고 카테고리별 지출을 주기적으로 확인해 보세요. 예산을 유지하면서 자주 지출하는 항목을 먼저 점검하되, 식비 기록이 빠진 날짜가 없는지도 확인하면 주기별 비교를 더 정확하게 할 수 있습니다.";
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private SpendingAnalysisRepository repository;
	@MockitoBean private OpenAiGateway gateway;

	@Test
	@Transactional
	void requestedResetPreservesOtherLedgersAndNewOrInFlightAnalyses() throws Exception {
		// Shadow the table inside this transaction to replay the one-time migration safely.
		jdbc.execute("CREATE TEMP TABLE spending_analyses AS SELECT * FROM public.spending_analyses WITH NO DATA");
		String ledger = "44df45be-f354-4896-bf7f-6f1ce74536ef";
		String other = UUID.randomUUID().toString();
		for (String owner : new String[] { ledger, other }) {
			jdbc.update("""
				INSERT INTO spending_analyses (ledger_id, period_from, period_to, status, summary, analyzed_at)
				VALUES (?::uuid, '2026-08-01', '2026-08-31', 'COMPLETED', '기존 분석', '2026-10-12 09:21:45+00')
				""", owner);
		}
		jdbc.update("""
			INSERT INTO spending_analyses (ledger_id, period_from, period_to, status, summary, analyzed_at)
			VALUES (?::uuid, '2026-09-01', '2026-09-30', 'COMPLETED', '새 분석', '2026-10-12 09:21:46+00'),
			       (?::uuid, '2026-07-01', '2026-07-31', 'PROCESSING', NULL, NULL)
			""", ledger, ledger);
		var migration = new ClassPathResource("db/migration/V20__clear_requested_ledger_spending_analyses.sql");
		try (var stream = migration.getInputStream()) {
			jdbc.execute(StreamUtils.copyToString(stream, StandardCharsets.UTF_8));
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM spending_analyses WHERE summary = '기존 분석' AND ledger_id = ?::uuid", Integer.class, ledger)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM spending_analyses WHERE ledger_id = ?::uuid", Integer.class, other)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM spending_analyses WHERE ledger_id = ?::uuid", Integer.class, ledger)).isEqualTo(2);
		assertThat(jdbc.queryForList("SELECT status FROM spending_analyses WHERE ledger_id = ?::uuid", String.class, ledger))
			.containsExactlyInAnyOrder("COMPLETED", "PROCESSING");
	}

	@Test
	void persistsSharedResultsAndPreventsRepeatedAndConcurrentProviderCalls() throws Exception {
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-08-25", "2026-09-24"))).andExpect(status().isUnauthorized());
		Cookie owner = bootstrap();
		Cookie member = member(owner);
		changeStart(owner);
		String categoryId = JsonPath.read(mvc.perform(get("/api/v1/categories").cookie(owner))
			.andReturn().getResponse().getContentAsString(), "$.items[0].id");
		createExpense(owner, categoryId, "2026-09-12", 18500);
		createExpense(member, categoryId, "2026-08-15", 9800);
		createExpense(member, categoryId, "2026-07-12", 3200);
		createExpense(member, categoryId, "2026-06-12", 4300);
		String ledgerText = mvc.perform(get("/api/v1/ledger").cookie(owner)).andReturn().getResponse().getContentAsString();
		UUID ledgerId = UUID.fromString(JsonPath.read(ledgerText, "$.id"));

		mvc.perform(get("/api/v1/statistics").cookie(member).param("from", "2026-08-25").param("to", "2026-09-24"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.spendingAnalysis.eligible").value(true))
			.andExpect(jsonPath("$.spendingAnalysis.status").value("NOT_ANALYZED"));
		mvc.perform(get("/api/v1/statistics/initial").cookie(member))
			.andExpect(jsonPath("$.statistics.spendingAnalysis.eligible").value(false));
		mvc.perform(post("/api/v1/statistics/analysis").cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-08-25", "2026-09-24"))).andExpect(status().isForbidden());
		for (String dates : new String[] { period("2026-09-25", "2026-10-24"), period("2026-08-25", "2026-09-23"),
			period("2026-06-25", "2026-09-24") }) {
			mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
				.content(dates)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ANALYSIS_CYCLE_REQUIRED"));
		}
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content("{}" )).andExpect(status().isBadRequest());
		verifyNoInteractions(gateway);

		providerSuccess();
		var generated = mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-08-25", "2026-09-24"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.analysis.status").value("COMPLETED"))
			.andExpect(jsonPath("$.analysis.text").value(SUMMARY))
			.andExpect(jsonPath("$.analysis.analyzedAt").isString()).andReturn();
		String savedAt = JsonPath.read(generated.getResponse().getContentAsString(), "$.analysis.analyzedAt");
		doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED", "비활성화")).when(gateway).generate(anyString(), anyString());
		for (Cookie session : new Cookie[] { owner, member }) {
			mvc.perform(get("/api/v1/statistics").cookie(session).param("from", "2026-08-25").param("to", "2026-09-24"))
				.andExpect(jsonPath("$.spendingAnalysis.text").value(SUMMARY))
				.andExpect(jsonPath("$.spendingAnalysis.analyzedAt").value(savedAt));
			mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content(period("2026-08-25", "2026-09-24"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.analysis.text").value(SUMMARY));
		}
		verify(gateway, times(1)).generate(anyString(), anyString());
		assertThat(jdbc.queryForObject("select input_tokens from spending_analyses where ledger_id = ?", Integer.class, ledgerId)).isEqualTo(150);

		Cookie outsider = ownLedger();
		changeStart(outsider);
		mvc.perform(get("/api/v1/statistics").cookie(outsider).param("from", "2026-08-25").param("to", "2026-09-24"))
			.andExpect(jsonPath("$.totalAmount").value(0)).andExpect(jsonPath("$.spendingAnalysis.status").value("NO_EXPENSES"))
			.andExpect(jsonPath("$.spendingAnalysis.eligible").value(false))
			.andExpect(jsonPath("$.spendingAnalysis.text").isEmpty());
		UUID outsiderLedgerId = UUID.fromString(JsonPath.read(mvc.perform(get("/api/v1/ledger").cookie(outsider))
			.andReturn().getResponse().getContentAsString(), "$.id"));
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(outsider).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-08-25", "2026-09-24"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ANALYSIS_NO_EXPENSES"));
		verify(gateway, times(1)).generate(anyString(), anyString());
		assertThat(jdbc.queryForObject("select count(*) from spending_analyses where ledger_id = ?", Integer.class, outsiderLedgerId)).isZero();
		String outsiderCategoryId = JsonPath.read(mvc.perform(get("/api/v1/categories").cookie(outsider))
			.andReturn().getResponse().getContentAsString(), "$.items[0].id");
		createExpense(outsider, outsiderCategoryId, "2026-09-12", 12000);
		mvc.perform(get("/api/v1/statistics").cookie(outsider).param("from", "2026-08-25").param("to", "2026-09-24"))
			.andExpect(jsonPath("$.spendingAnalysis.eligible").value(true))
			.andExpect(jsonPath("$.spendingAnalysis.status").value("NOT_ANALYZED"));

		doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_LIMITED", "사용 한도")).when(gateway).generate(anyString(), anyString());
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-07-25", "2026-08-24"))).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AI_PROVIDER_LIMITED"));
		mvc.perform(get("/api/v1/statistics").cookie(member).param("from", "2026-07-25").param("to", "2026-08-24"))
			.andExpect(jsonPath("$.spendingAnalysis.status").value("NOT_ANALYZED"));
		providerSuccess();
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-07-25", "2026-08-24"))).andExpect(status().isOk());

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
			var first = executor.submit(() -> mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content(period("2026-06-25", "2026-07-24"))).andExpect(status().isOk()));
			assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
			mvc.perform(get("/api/v1/statistics").cookie(member).param("from", "2026-06-25").param("to", "2026-07-24"))
				.andExpect(jsonPath("$.spendingAnalysis.status").value("PROCESSING"));
			mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
				.content(period("2026-06-25", "2026-07-24"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ANALYSIS_IN_PROGRESS"));
			release.countDown();
			first.get(15, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
		verify(gateway, times(4)).generate(anyString(), anyString());

		UUID staleToken = UUID.randomUUID();
		LocalDate from = LocalDate.parse("2026-05-25"), to = LocalDate.parse("2026-06-24");
		assertThat(repository.claim(ledgerId, from, to, staleToken, Instant.parse("2026-10-12T02:00:00Z"), Instant.parse("2026-10-12T02:01:00Z"))).isTrue();
		providerSuccess();
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content(period(from.toString(), to.toString()))).andExpect(status().isOk());
		assertThat(repository.complete(ledgerId, from, to, staleToken, "이전 요청", new AiTextResult("old", "이전 요청", 1, 1), Instant.now())).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from spending_analyses where ledger_id = ?", Integer.class, ledgerId)).isEqualTo(4);
		var inputs = ArgumentCaptor.forClass(String.class);
		verify(gateway, times(5)).generate(anyString(), inputs.capture());
		assertThat(inputs.getAllValues()).allSatisfy(input -> assertThat(input)
			.doesNotContain("PRIVATE_STORE", "PRIVATE_NOTE", "owner-analysis@example.com", ledgerId.toString()));
		assertThat(inputs.getAllValues().getFirst()).contains("18500", "800000");
		// Empty current records override an old cached result without another provider call.
		jdbc.update("delete from expenses where ledger_id = ? and spent_on between '2026-08-25' and '2026-09-24'", ledgerId);
		mvc.perform(get("/api/v1/statistics").cookie(member).param("from", "2026-08-25").param("to", "2026-09-24"))
			.andExpect(jsonPath("$.spendingAnalysis.status").value("NO_EXPENSES"))
			.andExpect(jsonPath("$.spendingAnalysis.text").isEmpty());
		mvc.perform(post("/api/v1/statistics/analysis").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
			.content(period("2026-08-25", "2026-09-24"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ANALYSIS_NO_EXPENSES"));
		verify(gateway, times(5)).generate(anyString(), anyString());
	}

	private void providerSuccess() {
		doAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(invocation.getArgument(0, String.class)).contains("약 300자", "280~320자", "최대 320자");
			return new AiTextResult("test-model", SUMMARY, 150, 50);
		}).when(gateway).generate(anyString(), anyString());
	}

	private Cookie bootstrap() throws Exception {
		var response = mvc.perform(post("/api/v1/bootstrap/admin").with(csrf()).header("X-Bootstrap-Token", "spending-analysis-bootstrap-token")
			.contentType(MediaType.APPLICATION_JSON).content("""
			{"email":"owner-analysis@example.com","password":"owner-password!","displayName":"관리자","ledgerName":"장부","defaultMonthlyBudget":800000}
			""")).andExpect(status().isCreated()).andReturn().getResponse();
		return cookie(response.getHeader("Set-Cookie"));
	}
	private Cookie member(Cookie owner) throws Exception {
		String code = JsonPath.read(mvc.perform(post("/api/v1/invitations").with(csrf()).cookie(owner)).andReturn().getResponse().getContentAsString(), "$.code");
		return cookie(mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
			{"inviteCode":"%s","email":"member-analysis@example.com","password":"member-password!","displayName":"멤버"}
			""".formatted(code))).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Set-Cookie"));
	}
	private Cookie ownLedger() throws Exception {
		return cookie(mvc.perform(post("/api/v1/auth/register-ledger").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
			{"email":"outsider-analysis@example.com","password":"outsider-password!","displayName":"별도 멤버","ledgerName":"별도 장부","defaultMonthlyBudget":800000}
			""")).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Set-Cookie"));
	}
	private void changeStart(Cookie session) throws Exception {
		mvc.perform(put("/api/v1/ledger/settings/budget-cycle").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"startDay\":25,\"version\":0}")).andExpect(status().isOk());
	}
	private void createExpense(Cookie session, String categoryId, String date, int amount) throws Exception {
		mvc.perform(post("/api/v1/expenses").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
			{"amount":%d,"spentOn":"%s","categoryId":"%s","merchant":"PRIVATE_STORE","memo":"PRIVATE_NOTE","imageIds":[]}
			""".formatted(amount, date, categoryId))).andExpect(status().isCreated());
	}
	private static String period(String from, String to) {
		return "{\"from\":\"%s\",\"to\":\"%s\"}".formatted(from, to);
	}
	private static Cookie cookie(String header) {
		String value = header.substring(header.indexOf("MBD_SESSION=") + 12).split(";", 2)[0];
		return new Cookie("MBD_SESSION", value);
	}
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean @Primary Clock analysisClock() {
			return Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneOffset.UTC);
		}
	}
}
