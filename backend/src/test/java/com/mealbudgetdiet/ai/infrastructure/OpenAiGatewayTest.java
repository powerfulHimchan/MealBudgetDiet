package com.mealbudgetdiet.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import com.mealbudgetdiet.shared.api.ApiException;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class OpenAiGatewayTest {

	private static final String SECRET = "test-key-never-return-to-client";
	private static final String COMPLETED = """
		{"status":"completed","model":"gpt-4.1-mini-2025-04-14","output":[
		  {"type":"reasoning","summary":[]},
		  {"type":"message","role":"assistant","content":[
		    {"type":"output_text","text":"Sikbi AI 연결 성공"}]}],
		 "usage":{"input_tokens":25,"output_tokens":8}}
		""";
	private final ObjectMapper mapper = new ObjectMapper();
	private final AtomicInteger status = new AtomicInteger(200);
	private final AtomicInteger requests = new AtomicInteger();
	private final AtomicReference<String> responseBody = new AtomicReference<>(COMPLETED);
	private final AtomicReference<String> requestBody = new AtomicReference<>();
	private final AtomicReference<String> authorization = new AtomicReference<>();
	private HttpServer server;
	private ExecutorService executor;
	private URI endpoint;

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		executor = Executors.newCachedThreadPool();
		server.setExecutor(executor);
		server.createContext("/v1/responses", exchange -> {
			requests.incrementAndGet();
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.getResponseHeaders().set("Location", "/v1/redirected");
			byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), body.length);
			try (var output = exchange.getResponseBody()) {
				output.write(body);
			}
		});
		server.start();
		endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
		executor.shutdownNow();
	}

	@Test
	void sendsBoundedStatelessRequestAndExtractsTextAndTokenUsage() {
		var result = gateway(true, SECRET).generate("Reply in Korean.", "연결 확인");
		assertThat(result.text()).isEqualTo("Sikbi AI 연결 성공");
		assertThat(result.model()).isEqualTo("gpt-4.1-mini-2025-04-14");
		assertThat(result.inputTokens()).isEqualTo(25);
		assertThat(result.outputTokens()).isEqualTo(8);
		var json = mapper.readTree(requestBody.get());
		assertThat(json.path("store").asBoolean()).isFalse();
		assertThat(json.path("max_output_tokens").asInt()).isEqualTo(512);
		assertThat(json.path("model").asString()).isEqualTo("gpt-4.1-mini");
		assertThat(json.path("input").asString()).isEqualTo("연결 확인");
		assertThat(json.path("instructions").asString()).isEqualTo("Reply in Korean.");
		assertThat(requestBody.get()).doesNotContain(SECRET);
		assertThat(authorization.get()).isEqualTo("Bearer " + SECRET);
	}

	@Test
	void disabledOrMissingKeyNeverMakesARequest() {
		assertError(() -> gateway(false, SECRET).generate("test", "test"), "AI_DISABLED", HttpStatus.SERVICE_UNAVAILABLE);
		assertError(() -> gateway(true, " ").generate("test", "test"), "AI_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(requests.get()).isZero();
	}

	@Test
	void rejectsBlankAndOversizedInputsWithoutCallingProvider() {
		assertError(() -> gateway(true, SECRET).generate("test", " "), "AI_INVALID_INPUT", HttpStatus.BAD_REQUEST);
		assertError(() -> gateway(true, SECRET).generate("test", "x".repeat(4001)), "AI_INVALID_INPUT", HttpStatus.BAD_REQUEST);
		assertThat(requests.get()).isZero();
	}

	@ParameterizedTest
	@CsvSource({
		"401, AI_PROVIDER_AUTH_FAILED, BAD_GATEWAY",
		"403, AI_PROVIDER_AUTH_FAILED, BAD_GATEWAY",
		"429, AI_PROVIDER_LIMITED, SERVICE_UNAVAILABLE",
		"400, AI_PROVIDER_REQUEST_FAILED, BAD_GATEWAY",
		"404, AI_PROVIDER_REQUEST_FAILED, BAD_GATEWAY",
		"500, AI_PROVIDER_UNAVAILABLE, BAD_GATEWAY",
		"302, AI_PROVIDER_UNAVAILABLE, BAD_GATEWAY"
	})
	void sanitizesProviderErrorsAndDoesNotRetryOrFollowRedirects(int upstreamStatus, String code, HttpStatus httpStatus) {
		status.set(upstreamStatus);
		responseBody.set(SECRET + " private submitted text");
		assertError(() -> gateway(true, SECRET).generate("test", "test"), code, httpStatus);
		assertThat(requests.get()).isEqualTo(1);
	}

	@Test
	void rejectsIncompleteResponseEvenWhenItContainsPartialText() {
		responseBody.set(COMPLETED.replace("completed", "incomplete"));
		assertError(() -> gateway(true, SECRET).generate("test", "test"), "AI_INCOMPLETE_RESPONSE", HttpStatus.BAD_GATEWAY);
	}

	@Test
	void rejectsRefusalAndMalformedResponses() {
		responseBody.set("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}");
		assertError(() -> gateway(true, SECRET).generate("test", "test"), "AI_EMPTY_RESPONSE", HttpStatus.BAD_GATEWAY);
		responseBody.set("not-json");
		assertError(() -> gateway(true, SECRET).generate("test", "test"), "AI_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);
	}

	@Test
	void reportsConnectionFailureWithoutRawExceptionDetails() {
		server.stop(0);
		assertError(() -> gateway(true, SECRET).generate("test", "test"), "AI_CONNECTION_FAILED", HttpStatus.BAD_GATEWAY);
	}

	@Test
	void redactsKeyInConfigurationToString() {
		assertThat(properties(true, SECRET).toString()).doesNotContain(SECRET).contains("REDACTED");
		assertThat(properties(true, SECRET).isTimeoutsValid()).isTrue();
	}

	private OpenAiGateway gateway(boolean enabled, String key) {
		return new OpenAiGateway(properties(enabled, key), mapper, endpoint);
	}

	private static OpenAiProperties properties(boolean enabled, String key) {
		return new OpenAiProperties(enabled, key, "gpt-4.1-mini", Duration.ofSeconds(1), Duration.ofSeconds(2), 512);
	}

	private static void assertError(Runnable action, String code, HttpStatus status) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getCode()).isEqualTo(code);
			assertThat(exception.getStatus()).isEqualTo(status);
			assertThat(exception.getMessage()).doesNotContain(SECRET, "private submitted text");
		});
	}
}
