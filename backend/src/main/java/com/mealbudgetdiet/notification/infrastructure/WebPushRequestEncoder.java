package com.mealbudgetdiet.notification.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jose4j.lang.JoseException;

import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;

final class WebPushRequestEncoder extends AbstractPushService<WebPushRequestEncoder> {

	WebPushRequestEncoder(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
		super(publicKey, privateKey, subject);
	}

	WebPushRequestEncoder(KeyPair keyPair, String subject) {
		super(keyPair, subject);
	}

	EncodedWebPushRequest encode(Notification notification)
		throws GeneralSecurityException, IOException, JoseException {
		var request = prepareRequest(notification, Encoding.AES128GCM);
		Map<String, String> headers = new LinkedHashMap<>(request.getHeaders());
		headers.computeIfPresent("Crypto-Key", (name, value) -> removeVapidKeyPadding(value));
		return new EncodedWebPushRequest(URI.create(request.getUrl()), headers, request.getBody());
	}

	/*
	 * web-push 5.1.2 adds Base64 padding to p256ecdsa. Some push services reject that
	 * header with HTTP 403. Keep the compatibility fix inside this adapter so it can be
	 * removed when a corrected version is available from Maven Central.
	 */
	static String removeVapidKeyPadding(String cryptoKey) {
		int marker = cryptoKey.indexOf("p256ecdsa=");
		if (marker < 0) {
			return cryptoKey;
		}
		int valueStart = marker + "p256ecdsa=".length();
		int valueEnd = cryptoKey.indexOf(';', valueStart);
		if (valueEnd < 0) {
			valueEnd = cryptoKey.length();
		}
		int paddingStart = valueEnd;
		while (paddingStart > valueStart && cryptoKey.charAt(paddingStart - 1) == '=') {
			paddingStart--;
		}
		return cryptoKey.substring(0, paddingStart) + cryptoKey.substring(valueEnd);
	}

	record EncodedWebPushRequest(URI uri, Map<String, String> headers, byte[] body) {
		EncodedWebPushRequest {
			headers = Map.copyOf(headers);
			body = body == null ? new byte[0] : body.clone();
		}

		@Override
		public byte[] body() {
			return body.clone();
		}
	}
}
