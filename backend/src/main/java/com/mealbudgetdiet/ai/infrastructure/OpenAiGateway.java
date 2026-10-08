package com.mealbudgetdiet.ai.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.ai.application.AiTextResult;
import com.mealbudgetdiet.shared.api.ApiException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiGateway {

	private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
	private static final int MAX_INPUT_LENGTH = 4000;
	private final OpenAiProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final URI endpoint;

	@Autowired
	public OpenAiGateway(OpenAiProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, RESPONSES_URI);
	}

	// A local endpoint is injected only by transport tests; production uses the fixed HTTPS URI.
	OpenAiGateway(OpenAiProperties properties, ObjectMapper objectMapper, URI endpoint) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.endpoint = endpoint;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	}

	public void requireReady() {
		if (!properties.enabled()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED", "AI 연결이 비활성화되어 있습니다.");
		}
		if (!properties.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "서버에 OpenAI API 키가 설정되지 않았습니다.");
		}
	}

	public AiTextResult generate(String instructions, String input) {
		requireReady();
		if (instructions == null || instructions.isBlank() || instructions.length() > MAX_INPUT_LENGTH
			|| input == null || input.isBlank() || input.length() > MAX_INPUT_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AI_INVALID_INPUT", "AI 입력은 1~4000자여야 합니다.");
		}
		try {
			var body = objectMapper.writeValueAsString(Map.of(
				"model", properties.model(), "instructions", instructions, "input", input,
				"store", false, "max_output_tokens", properties.maxOutputTokens()));
			var request = HttpRequest.newBuilder(endpoint)
				.timeout(properties.requestTimeout())
				.header("Authorization", "Bearer " + properties.apiKey().trim())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			// No automatic retries: a repeated generation may be billed twice.
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw upstreamError(response.statusCode());
			}
			var json = objectMapper.readTree(response.body());
			if (!"completed".equals(json.path("status").asString())) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INCOMPLETE_RESPONSE", "AI 응답이 완료되지 않았습니다. 응답 길이 설정을 확인해 주세요.");
			}
			var text = new StringBuilder();
			for (var item : json.path("output")) {
				if (!"message".equals(item.path("type").asString())
					|| !"assistant".equals(item.path("role").asString())) {
					continue;
				}
				for (var content : item.path("content")) {
					if ("output_text".equals(content.path("type").asString())) {
						text.append(content.path("text").asString(""));
					}
				}
			}
			if (text.toString().isBlank()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "AI가 텍스트 응답을 반환하지 않았습니다.");
			}
			return new AiTextResult(json.path("model").asString(properties.model()), text.toString(),
				json.path("usage").path("input_tokens").asInt(0),
				json.path("usage").path("output_tokens").asInt(0));
		} catch (HttpTimeoutException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 응답 시간이 초과되었습니다.");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERRUPTED", "AI 요청이 중단되었습니다.");
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_CONNECTION_FAILED", "OpenAI 서버에 연결할 수 없습니다.");
		} catch (JacksonException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "AI 응답을 처리할 수 없습니다.");
		}
	}

	private static ApiException upstreamError(int status) {
		// Never return upstream bodies: they can contain submitted text or account details.
		if (status == 401 || status == 403) {
			return new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_AUTH_FAILED", "OpenAI API 키와 사용 권한을 확인해 주세요.");
		}
		if (status == 429) {
			return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_LIMITED", "OpenAI 사용 한도 또는 잔액을 확인해 주세요.");
		}
		if (status == 400 || status == 404) {
			return new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_REQUEST_FAILED", "OpenAI 모델과 요청 설정을 확인해 주세요.");
		}
		return new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_UNAVAILABLE", "OpenAI 요청을 처리할 수 없습니다.");
	}
}
