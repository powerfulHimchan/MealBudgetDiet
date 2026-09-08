package com.mealbudgetdiet.identity.application;

import java.util.Locale;

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
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
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

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
