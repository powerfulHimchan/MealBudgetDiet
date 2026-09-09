package com.mealbudgetdiet.notification.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.notification.application.PushSubscriptionService;
import com.mealbudgetdiet.notification.application.PushSubscriptionSnapshot;
import com.mealbudgetdiet.notification.domain.PushSubscriptionStatus;
import com.mealbudgetdiet.shared.api.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
public class PushController {

	private final PushSubscriptionService subscriptionService;
	private final String vapidPublicKey;

	public PushController(
		PushSubscriptionService subscriptionService,
		@Value("${app.push.vapid-public-key:}") String vapidPublicKey
	) {
		this.subscriptionService = subscriptionService;
		this.vapidPublicKey = vapidPublicKey;
	}

	@GetMapping("/api/v1/push/vapid-public-key")
	VapidKeyResponse vapidPublicKey() {
		if (vapidPublicKey.isBlank()) {
			throw new ApiException(
				HttpStatus.SERVICE_UNAVAILABLE, "PUSH_NOT_CONFIGURED", "푸시 알림이 아직 설정되지 않았습니다.");
		}
		return new VapidKeyResponse(vapidPublicKey);
	}

	@PutMapping("/api/v1/push-subscriptions")
	ResponseEntity<PushSubscriptionResponse> register(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody PushSubscriptionRequest request
	) {
		var snapshot = subscriptionService.register(
			principal.id(), request.endpoint(), request.keys().p256dh(), request.keys().auth());
		return ResponseEntity.status(snapshot.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(PushSubscriptionResponse.from(snapshot));
	}

	@DeleteMapping("/api/v1/push-subscriptions/{subscriptionId}")
	ResponseEntity<Void> disable(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID subscriptionId
	) {
		subscriptionService.disable(principal.id(), subscriptionId);
		return ResponseEntity.noContent().build();
	}

	public record PushSubscriptionRequest(
		@NotBlank(message = "푸시 endpoint가 필요합니다.")
		@Size(max = 4096, message = "푸시 endpoint가 너무 깁니다.")
		String endpoint,
		Long expirationTime,
		@NotNull(message = "푸시 암호화 키가 필요합니다.")
		@Valid PushKeysRequest keys
	) {
	}

	public record PushKeysRequest(
		@NotBlank(message = "p256dh 키가 필요합니다.")
		@Size(max = 512, message = "p256dh 키가 너무 깁니다.")
		String p256dh,
		@NotBlank(message = "auth 키가 필요합니다.")
		@Size(max = 512, message = "auth 키가 너무 깁니다.")
		String auth
	) {
	}

	private record VapidKeyResponse(String publicKey) {
	}

	private record PushSubscriptionResponse(UUID id, PushSubscriptionStatus status, Instant createdAt) {
		static PushSubscriptionResponse from(PushSubscriptionSnapshot snapshot) {
			return new PushSubscriptionResponse(snapshot.id(), snapshot.status(), snapshot.createdAt());
		}
	}
}
