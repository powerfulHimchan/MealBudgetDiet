package com.mealbudgetdiet.identity.application;

import java.util.Locale;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mealbudgetdiet.identity.domain.User;
import com.mealbudgetdiet.identity.infrastructure.UserRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class IdentityService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public IdentityService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public User createUser(String email, String password, String displayName) {
		String normalizedEmail = normalizeEmail(email);
		var existingUser = userRepository.findByEmail(normalizedEmail);
		if (existingUser.isPresent()) {
			if (existingUser.get().getStatus() == com.mealbudgetdiet.identity.domain.UserStatus.ACTIVE) {
				throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
			}
			existingUser.get().reactivate(passwordEncoder.encode(password), displayName.trim());
			return userRepository.saveAndFlush(existingUser.get());
		}
		return userRepository.saveAndFlush(new User(
			normalizedEmail,
			passwordEncoder.encode(password),
			displayName.trim()
		));
	}

	public long countUsers() {
		return userRepository.count();
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

	public void withdraw(User user) {
		user.withdraw();
	}

	public void deleteUsers(Collection<User> users) {
		userRepository.deleteAllInBatch(users);
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
