package com.mealbudgetdiet.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthSubjectHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public AuthSubjectHasher(
		@Value("${app.auth-security.rate-limit-secret:local-development-rate-limit-secret-change-me}") String secret
	) {
		if (secret == null || secret.length() < 32) {
			throw new IllegalArgumentException("Auth rate limit secret must contain at least 32 characters");
		}
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String hash(String namespace, String subject) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(
				(namespace + '\0' + subject).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("HMAC-SHA256 is not available", exception);
		}
	}
}
