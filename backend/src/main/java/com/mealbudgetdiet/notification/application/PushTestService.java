package com.mealbudgetdiet.notification.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.mealbudgetdiet.identity.application.AuthSubjectHasher;
import com.mealbudgetdiet.notification.infrastructure.WebPushGateway;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class PushTestService {

	private final JdbcClient jdbc;
	private final WebPushGateway gateway;
	private final AuthSubjectHasher hasher;
	private final Clock clock;

	public PushTestService(JdbcClient jdbc, WebPushGateway gateway, AuthSubjectHasher hasher, Clock clock) {
		this.jdbc = jdbc;
		this.gateway = gateway;
		this.hasher = hasher;
		this.clock = clock;
	}

	public void send(UUID userId, UUID subscriptionId) {
		var target = jdbc.sql("""
			select endpoint, p256dh_key, auth_key from push_subscriptions
			where id = :id and user_id = :userId and status = 'ACTIVE'
			""")
			.param("id", subscriptionId).param("userId", userId)
			.query((row, index) -> new Target(row.getString(1), row.getString(2), row.getString(3)))
			.optional().orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "PUSH_SUBSCRIPTION_NOT_FOUND", "활성화된 기기 구독을 찾을 수 없습니다."));
		if (!gateway.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PUSH_NOT_CONFIGURED", "푸시 알림이 아직 설정되지 않았습니다.");
		}

		// Autocommit reserves the cooldown before network I/O, including on failed sends.
		var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		boolean acquired = jdbc.sql("""
			insert into auth_rate_limits (scope, subject_hash, window_started_at, attempt_count, updated_at)
			values ('PUSH_TEST', :subject, :now, 1, :now)
			on conflict (scope, subject_hash) do update set
			  window_started_at = excluded.window_started_at, attempt_count = 1, updated_at = excluded.updated_at
			where auth_rate_limits.updated_at <= :cutoff
			returning attempt_count
			""")
			.param("subject", hasher.hash("push-test", userId.toString()))
			.param("now", now).param("cutoff", now.minusSeconds(60))
			.query(Integer.class).optional().isPresent();
		if (!acquired) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PUSH_TEST_RATE_LIMITED", "테스트 알림은 1분에 한 번 보낼 수 있습니다.");
		}
		var result = gateway.send(target.endpoint(), target.p256dh(), target.auth(), """
			{"notificationId":"%s","type":"PUSH_TEST","title":"Sikbi 테스트 알림","body":"이 기기에서 알림을 받을 수 있어요.","data":{"url":"/settings/notifications"}}
			""".formatted(UUID.randomUUID()).strip());
		if (result.expired()) {
			jdbc.sql("update push_subscriptions set status = 'EXPIRED', updated_at = current_timestamp where id = :id and user_id = :userId")
				.param("id", subscriptionId).param("userId", userId).update();
			throw new ApiException(HttpStatus.GONE, "PUSH_SUBSCRIPTION_EXPIRED", "기기 구독이 만료되었습니다. 알림을 해제한 뒤 다시 활성화해 주세요.");
		}
		if (!result.success()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "PUSH_SEND_FAILED", "테스트 알림을 전송하지 못했습니다. 잠시 후 다시 시도해 주세요.");
		}
	}

	private record Target(String endpoint, String p256dh, String auth) { }
}
