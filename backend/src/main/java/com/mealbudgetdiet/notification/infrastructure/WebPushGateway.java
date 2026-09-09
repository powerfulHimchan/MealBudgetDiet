package com.mealbudgetdiet.notification.infrastructure;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.notification.application.PushDeliveryTask;
import com.mealbudgetdiet.notification.application.PushSendResult;

import nl.martijndwars.webpush.Notification;

@Component
public class WebPushGateway {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	private final String publicKey;
	private final String privateKey;
	private final String subject;
	private final HttpClient httpClient;

	public WebPushGateway(
		@Value("${app.push.vapid-public-key:}") String publicKey,
		@Value("${app.push.vapid-private-key:}") String privateKey,
		@Value("${app.push.vapid-subject:}") String subject
	) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.subject = subject;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	public boolean isConfigured() {
		return !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank();
	}

	public PushSendResult send(PushDeliveryTask task, String payload) {
		if (!isConfigured()) {
			return PushSendResult.failed(false, false, "VAPID 설정이 없습니다.");
		}
		try {
			var encoder = new WebPushRequestEncoder(publicKey, privateKey, subject);
			var notification = new Notification(task.endpoint(), task.p256dhKey(), task.authKey(), payload);
			var encoded = encoder.encode(notification);
			var requestBuilder = HttpRequest.newBuilder(encoded.uri())
				.timeout(REQUEST_TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofByteArray(encoded.body()));
			encoded.headers().forEach(requestBuilder::header);

			int status = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
			if (status >= 200 && status < 300) {
				return PushSendResult.sent();
			}
			boolean expired = isExpiredStatus(status);
			boolean retryable = isRetryableStatus(status);
			return PushSendResult.failed(expired, retryable, "Push service HTTP " + status);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return PushSendResult.failed(false, true, "Web Push 전송이 중단되었습니다.");
		} catch (IOException exception) {
			return PushSendResult.failed(false, true, exception.getClass().getSimpleName());
		} catch (GeneralSecurityException | JoseException | IllegalArgumentException exception) {
			return PushSendResult.failed(false, false, exception.getClass().getSimpleName());
		}
	}

	static boolean isExpiredStatus(int status) {
		return status == 404 || status == 410;
	}

	static boolean isRetryableStatus(int status) {
		return status == 408 || status == 425 || status == 429 || status >= 500;
	}
}
