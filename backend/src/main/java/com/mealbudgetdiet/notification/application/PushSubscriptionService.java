package com.mealbudgetdiet.notification.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.notification.domain.PushSubscription;
import com.mealbudgetdiet.notification.infrastructure.PushSubscriptionRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class PushSubscriptionService {

	private final PushSubscriptionRepository repository;

	public PushSubscriptionService(PushSubscriptionRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public PushSubscriptionSnapshot register(
		UUID userId,
		String endpoint,
		String p256dhKey,
		String authKey
	) {
		validateEndpoint(endpoint);
		var existing = repository.findByEndpoint(endpoint);
		boolean created = existing.isEmpty();
		var subscription = existing.orElseGet(() -> new PushSubscription(userId, endpoint, p256dhKey, authKey));
		if (!created) {
			subscription.activateFor(userId, p256dhKey, authKey);
		}
		var saved = repository.saveAndFlush(subscription);
		return snapshot(saved, created);
	}

	private static void validateEndpoint(String endpoint) {
		try {
			URI uri = new URI(endpoint);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			boolean trustedPushService = host.equals("fcm.googleapis.com")
				|| host.endsWith(".push.services.mozilla.com")
				|| host.equals("push.services.mozilla.com")
				|| host.endsWith(".push.apple.com")
				|| host.equals("web.push.apple.com")
				|| host.endsWith(".notify.windows.com");
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !trustedPushService) {
				throw invalidEndpoint();
			}
		} catch (URISyntaxException exception) {
			throw invalidEndpoint();
		}
	}

	private static ApiException invalidEndpoint() {
		return new ApiException(
			HttpStatus.BAD_REQUEST, "PUSH_ENDPOINT_INVALID", "지원하지 않는 Web Push endpoint입니다.");
	}

	@Transactional
	public void disable(UUID userId, UUID subscriptionId) {
		var subscription = repository.findByIdAndUserId(subscriptionId, userId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "PUSH_SUBSCRIPTION_NOT_FOUND", "푸시 구독을 찾을 수 없습니다."));
		subscription.disable();
		repository.flush();
	}

	private static PushSubscriptionSnapshot snapshot(PushSubscription subscription, boolean created) {
		return new PushSubscriptionSnapshot(
			subscription.getId(), subscription.getStatus(), subscription.getCreatedAt(), created);
	}
}
