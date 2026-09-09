package com.mealbudgetdiet.notification.infrastructure;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.notification.application.PushDeliveryTask;
import com.mealbudgetdiet.notification.application.PushSendResult;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Component
public class WebPushGateway {

	private final String publicKey;
	private final String privateKey;
	private final String subject;

	public WebPushGateway(
		@Value("${app.push.vapid-public-key:}") String publicKey,
		@Value("${app.push.vapid-private-key:}") String privateKey,
		@Value("${app.push.vapid-subject:}") String subject
	) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.subject = subject;
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	public boolean isConfigured() {
		return !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank();
	}

	public PushSendResult send(PushDeliveryTask task, String payload) {
		if (!isConfigured()) {
			return PushSendResult.failed(false, "VAPID 설정이 없습니다.");
		}
		try {
			var pushService = new PushService(publicKey, privateKey, subject);
			var notification = new Notification(task.endpoint(), task.p256dhKey(), task.authKey(), payload);
			var response = pushService.send(notification);
			int status = response.getStatusLine().getStatusCode();
			if (status >= 200 && status < 300) {
				return PushSendResult.sent();
			}
			return PushSendResult.failed(status == 404 || status == 410, "Push service HTTP " + status);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return PushSendResult.failed(false, "Web Push 전송이 중단되었습니다.");
		} catch (Exception exception) {
			return PushSendResult.failed(false, exception.getClass().getSimpleName());
		}
	}
}
