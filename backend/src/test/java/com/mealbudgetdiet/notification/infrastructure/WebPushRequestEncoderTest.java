package com.mealbudgetdiet.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nl.martijndwars.webpush.Notification;

class WebPushRequestEncoderTest {

	@BeforeAll
	static void registerProvider() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	@Test
	void removesOnlyVapidKeyPaddingFromCryptoKeyHeader() {
		assertThat(WebPushRequestEncoder.removeVapidKeyPadding("dh=abc=;p256ecdsa=key=="))
			.isEqualTo("dh=abc=;p256ecdsa=key");
		assertThat(WebPushRequestEncoder.removeVapidKeyPadding("p256ecdsa=key==;dh=abc="))
			.isEqualTo("p256ecdsa=key;dh=abc=");
		assertThat(WebPushRequestEncoder.removeVapidKeyPadding("dh=abc="))
			.isEqualTo("dh=abc=");
	}

	@Test
	void createsAes128GcmRequestWithUnpaddedVapidKey() throws Exception {
		KeyPair serverKeys = generateKeyPair();
		KeyPair userKeys = generateKeyPair();
		var encoder = new WebPushRequestEncoder(serverKeys, "mailto:test@example.com");
		var notification = new Notification(
			"https://updates.push.services.mozilla.com/wpush/v2/test",
			userKeys.getPublic(), new byte[16], "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		var request = encoder.encode(notification);

		assertThat(request.headers()).containsEntry("Content-Encoding", "aes128gcm");
		assertThat(request.headers().get("Authorization")).startsWith("vapid t=");
		assertThat(request.headers().get("Crypto-Key"))
			.startsWith("p256ecdsa=")
			.doesNotEndWith("=");
		assertThat(request.body()).isNotEmpty();
	}

	private KeyPair generateKeyPair() throws Exception {
		var generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
		generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
		return generator.generateKeyPair();
	}
}
