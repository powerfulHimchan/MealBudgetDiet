package com.mealbudgetdiet;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.mealbudgetdiet.media.infrastructure.ImageObjectStore;
import com.mealbudgetdiet.identity.application.PasswordResetEmailSender;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

	@Bean
	@Primary
	ImageObjectStore testImageObjectStore() {
		var objects = new ConcurrentHashMap<String, byte[]>();
		return new ImageObjectStore() {
			@Override public void put(String key, byte[] content, String contentType) {
				objects.put(key, content.clone());
			}

			@Override public byte[] get(String key) {
				byte[] content = objects.get(key);
				return content == null ? null : content.clone();
			}

			@Override public void delete(String key) {
				objects.remove(key);
			}
		};
	}

	@Bean
	@Primary
	TestPasswordResetEmailSender testPasswordResetEmailSender() {
		return new TestPasswordResetEmailSender();
	}

	public static final class TestPasswordResetEmailSender implements PasswordResetEmailSender {

		private final ConcurrentLinkedDeque<PasswordResetEmail> messages = new ConcurrentLinkedDeque<>();

		@Override
		public void send(String email, String displayName, URI resetLink) {
			messages.addLast(new PasswordResetEmail(email, displayName, resetLink));
		}

		public PasswordResetEmail latest() {
			return messages.getLast();
		}

		public int size() {
			return messages.size();
		}

		public void clear() {
			messages.clear();
		}
	}

	public record PasswordResetEmail(String email, String displayName, URI resetLink) {
	}

}
