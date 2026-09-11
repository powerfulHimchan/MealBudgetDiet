package com.mealbudgetdiet.identity.application;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.identity.domain.ServiceRole;
import com.mealbudgetdiet.identity.domain.User;
import com.mealbudgetdiet.identity.domain.UserStatus;
import com.mealbudgetdiet.identity.infrastructure.PasswordResetTokenRepository;
import com.mealbudgetdiet.identity.infrastructure.UserRepository;
import com.mealbudgetdiet.notification.infrastructure.PushSubscriptionRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class IdentityService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PushSubscriptionRepository pushSubscriptionRepository;

	public IdentityService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		PasswordResetTokenRepository passwordResetTokenRepository,
		PushSubscriptionRepository pushSubscriptionRepository
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.pushSubscriptionRepository = pushSubscriptionRepository;
	}

	public User createUser(String email, String password, String displayName) {
		return createUser(email, password, displayName, ServiceRole.USER);
	}

	public User createUser(String email, String password, String displayName, ServiceRole serviceRole) {
		String normalizedEmail = normalizeEmail(email);
		var existingUser = userRepository.findByEmail(normalizedEmail);
		if (existingUser.isPresent()) {
			if (existingUser.get().getStatus() == UserStatus.ACTIVE) {
				throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
			}
			existingUser.get().reactivate(passwordEncoder.encode(password), displayName.trim(), serviceRole);
			return userRepository.saveAndFlush(existingUser.get());
		}
		return userRepository.saveAndFlush(new User(
			normalizedEmail,
			passwordEncoder.encode(password),
			displayName.trim(),
			serviceRole
		));
	}

	public boolean hasActiveServiceAdmin() {
		return userRepository.existsByServiceRoleAndStatus(ServiceRole.SERVICE_ADMIN, UserStatus.ACTIVE);
	}

	public User getUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
	}

	public List<User> getUsers(Collection<UUID> userIds) {
		return userRepository.findAllById(userIds);
	}

	public boolean passwordMatches(User user, String rawPassword) {
		return user.getPasswordHash() != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
	}

	@Transactional
	public String changePassword(UUID userId, String currentPassword, String newPassword) {
		User user = getUser(userId);
		if (!passwordMatches(user, currentPassword)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "현재 비밀번호가 올바르지 않습니다.");
		}
		user.changePassword(passwordEncoder.encode(newPassword));
		return user.getEmail();
	}

	public void withdraw(User user) {
		passwordResetTokenRepository.deleteAllByUserId(user.getId());
		pushSubscriptionRepository.deleteAllByUserId(user.getId());
		user.withdraw();
	}

	public void deleteUsers(Collection<User> users) {
		userRepository.deleteAllInBatch(users);
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
