package com.mealbudgetdiet.identity.application;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mealbudgetdiet.identity.domain.PasswordResetToken;
import com.mealbudgetdiet.identity.domain.UserStatus;
import com.mealbudgetdiet.identity.infrastructure.PasswordResetTokenRepository;
import com.mealbudgetdiet.identity.infrastructure.UserRepository;
import com.mealbudgetdiet.shared.api.ApiException;
import com.mealbudgetdiet.shared.security.TokenHasher;

@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private final PasswordResetTokenRepository tokenRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetEmailSender emailSender;
	private final TokenHasher tokenHasher;
	private final Clock clock;
	private final Duration tokenTtl;
	private final String publicBaseUrl;
	private final SecureRandom secureRandom = new SecureRandom();

	public PasswordResetService(
		PasswordResetTokenRepository tokenRepository,
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		PasswordResetEmailSender emailSender,
		TokenHasher tokenHasher,
		Clock clock,
		@Value("${app.password-reset.token-ttl:30m}") Duration tokenTtl,
		@Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl
	) {
		this.tokenRepository = tokenRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailSender = emailSender;
		this.tokenHasher = tokenHasher;
		this.clock = clock;
		if (tokenTtl.isZero() || tokenTtl.isNegative()) {
			throw new IllegalArgumentException("Password reset token TTL must be positive");
		}
		this.tokenTtl = tokenTtl;
		String normalizedBaseUrl = publicBaseUrl.replaceAll("/+$", "");
		URI baseUri = URI.create(normalizedBaseUrl);
		if (baseUri.getHost() == null || !("http".equalsIgnoreCase(baseUri.getScheme())
			|| "https".equalsIgnoreCase(baseUri.getScheme()))) {
			throw new IllegalArgumentException("Public base URL must be an absolute HTTP(S) URL");
		}
		this.publicBaseUrl = normalizedBaseUrl;
	}

	@Transactional
	public void requestReset(String email) {
		var user = userRepository.findByEmailForUpdate(IdentityService.normalizeEmail(email))
			.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
			.orElse(null);
		if (user == null) return;

		var now = clock.instant();
		tokenRepository.markUnusedTokensUsed(user.getId(), now);
		String rawToken = generateToken();
		tokenRepository.saveAndFlush(new PasswordResetToken(
			user.getId(), tokenHasher.hashExact(rawToken), now.plus(tokenTtl)));
		URI resetLink = URI.create(publicBaseUrl + "/reset-password?token=" + rawToken);
		sendAfterCommit(user.getEmail(), user.getDisplayName(), resetLink);
	}

	@Transactional
	public String resetPassword(String rawToken, String newPassword) {
		var now = clock.instant();
		String tokenHash = tokenHasher.hashExact(rawToken);
		var tokenOwnerId = tokenRepository.findByTokenHash(tokenHash)
			.map(PasswordResetToken::getUserId)
			.orElseThrow(PasswordResetService::invalidToken);
		var user = userRepository.findByIdForUpdate(tokenOwnerId)
			.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(PasswordResetService::invalidToken);
		var token = tokenRepository.findByTokenHashForUpdate(tokenHash)
			.orElseThrow(PasswordResetService::invalidToken);
		if (!token.isUsableAt(now)) throw invalidToken();

		user.changePassword(passwordEncoder.encode(newPassword));
		token.markUsed(now);
		return user.getEmail();
	}

	private String generateToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private void sendAfterCommit(String email, String displayName, URI resetLink) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					emailSender.send(email, displayName, resetLink);
				}
				catch (RuntimeException exception) {
					log.error("Failed to send a password reset email", exception);
				}
			}
		});
	}

	private static ApiException invalidToken() {
		return new ApiException(
			HttpStatus.BAD_REQUEST,
			"PASSWORD_RESET_TOKEN_INVALID",
			"비밀번호 재설정 링크가 유효하지 않거나 만료되었습니다."
		);
	}
}
