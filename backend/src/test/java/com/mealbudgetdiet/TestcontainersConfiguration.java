package com.mealbudgetdiet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.mealbudgetdiet.media.infrastructure.ImageObjectStore;

import java.util.concurrent.ConcurrentHashMap;

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

}
