package com.mealbudgetdiet.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRateLimitService {

	private static final String LOGIN_SCOPE = "LOGIN_FAILURE";
	private static final String RESET_EMAIL_SCOPE = "PASSWORD_RESET_EMAIL";
	private static final String RESET_CLIENT_SCOPE = "PASSWORD_RESET_CLIENT";

	private final JdbcClient jdbcClient;
	private final AuthSubjectHasher subjectHasher;
	private final Clock clock;
	private final int loginMaxFailures;
	private final Duration loginWindow;
	private final int resetEmailMaxRequests;
	private final int resetClientMaxRequests;
	private final Duration resetWindow;
	private final Duration retention;

	public AuthRateLimitService(
		JdbcClient jdbcClient,
		AuthSubjectHasher subjectHasher,
		Clock clock,
		@Value("${app.auth-security.login.max-failures:5}") int loginMaxFailures,
		@Value("${app.auth-security.login.window:15m}") Duration loginWindow,
		@Value("${app.auth-security.password-reset.max-requests-per-email:3}") int resetEmailMaxRequests,
		@Value("${app.auth-security.password-reset.max-requests-per-client:10}") int resetClientMaxRequests,
		@Value("${app.auth-security.password-reset.window:1h}") Duration resetWindow,
		@Value("${app.auth-security.retention:7d}") Duration retention
	) {
		this.jdbcClient = jdbcClient;
		this.subjectHasher = subjectHasher;
		this.clock = clock;
		this.loginMaxFailures = positive(loginMaxFailures, "Login max failures");
		this.loginWindow = positive(loginWindow, "Login rate limit window");
		this.resetEmailMaxRequests = positive(resetEmailMaxRequests, "Password reset email limit");
		this.resetClientMaxRequests = positive(resetClientMaxRequests, "Password reset client limit");
		this.resetWindow = positive(resetWindow, "Password reset rate limit window");
		this.retention = positive(retention, "Auth rate limit retention");
		if (this.retention.compareTo(this.loginWindow) < 0
			|| this.retention.compareTo(this.resetWindow) < 0) {
			throw new IllegalArgumentException("Auth rate limit retention must cover every rate limit window");
		}
	}

	@Transactional
	public void acquireLoginAttempt(String email, String clientAddress) {
		String normalizedEmail = IdentityService.normalizeEmail(email);
		String subjectHash = subjectHasher.hash("login", normalizedEmail + '\0' + safeClient(clientAddress));
		var decision = consume(LOGIN_SCOPE, subjectHash, loginMaxFailures, loginWindow);
		if (!decision.allowed()) {
			throw new AuthRateLimitException(decision.retryAfterSeconds());
		}
	}

	@Transactional
	public void clearLoginFailures(String email, String clientAddress) {
		String normalizedEmail = IdentityService.normalizeEmail(email);
		String subjectHash = subjectHasher.hash("login", normalizedEmail + '\0' + safeClient(clientAddress));
		jdbcClient.sql("delete from auth_rate_limits where scope = :scope and subject_hash = :subjectHash")
			.param("scope", LOGIN_SCOPE)
			.param("subjectHash", subjectHash)
			.update();
	}

	@Transactional
	public boolean acquirePasswordResetRequest(String email, String clientAddress) {
		String normalizedEmail = IdentityService.normalizeEmail(email);
		var emailDecision = consume(
			RESET_EMAIL_SCOPE,
			subjectHasher.hash("password-reset-email", normalizedEmail),
			resetEmailMaxRequests,
			resetWindow
		);
		var clientDecision = consume(
			RESET_CLIENT_SCOPE,
			subjectHasher.hash("password-reset-client", safeClient(clientAddress)),
			resetClientMaxRequests,
			resetWindow
		);
		return emailDecision.allowed() && clientDecision.allowed();
	}

	@Scheduled(cron = "${app.auth-security.cleanup-cron:0 0 4 * * *}", zone = "UTC")
	@Transactional
	public void removeExpiredRateLimits() {
		jdbcClient.sql("delete from auth_rate_limits where updated_at < :cutoff")
			.param("cutoff", toUtcOffsetDateTime(clock.instant().minus(retention)))
			.update();
	}

	private RateLimitDecision consume(String scope, String subjectHash, int limit, Duration window) {
		Instant now = clock.instant();
		Instant windowStart = fixedWindowStart(now, window);
		int attemptCount = jdbcClient.sql("""
			insert into auth_rate_limits (
				scope, subject_hash, window_started_at, attempt_count, updated_at
			) values (
				:scope, :subjectHash, :windowStart, 1, :now
			)
			on conflict (scope, subject_hash) do update set
				window_started_at = case
					when auth_rate_limits.window_started_at < excluded.window_started_at
					then excluded.window_started_at
					else auth_rate_limits.window_started_at
				end,
				attempt_count = case
					when auth_rate_limits.window_started_at < excluded.window_started_at then 1
					else least(auth_rate_limits.attempt_count + 1, :counterCeiling)
				end,
				updated_at = excluded.updated_at
			returning attempt_count
			""")
			.param("scope", scope)
			.param("subjectHash", subjectHash)
			.param("windowStart", toUtcOffsetDateTime(windowStart))
			.param("now", toUtcOffsetDateTime(now))
			.param("counterCeiling", limit + 1)
			.query(Integer.class)
			.single();
		long retryAfterSeconds = Math.max(1, windowStart.plus(window).getEpochSecond() - now.getEpochSecond());
		return new RateLimitDecision(attemptCount <= limit, retryAfterSeconds);
	}

	private static Instant fixedWindowStart(Instant now, Duration window) {
		long windowSeconds = window.getSeconds();
		return Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds);
	}

	private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static int positive(int value, String name) {
		if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
		return value;
	}

	private static Duration positive(Duration value, String name) {
		if (value.isZero() || value.isNegative() || value.getSeconds() == 0) {
			throw new IllegalArgumentException(name + " must be at least one second");
		}
		return value;
	}

	private static String safeClient(String clientAddress) {
		return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
	}

	private record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
	}
}
