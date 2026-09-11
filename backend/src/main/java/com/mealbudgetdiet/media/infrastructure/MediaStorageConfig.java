package com.mealbudgetdiet.media.infrastructure;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
public class MediaStorageConfig {

	@Bean
	S3Client mediaS3Client(
		@Value("${app.media.endpoint}") URI endpoint,
		@Value("${app.media.region}") String region,
		@Value("${app.media.access-key}") String accessKey,
		@Value("${app.media.secret-key}") String secretKey,
		@Value("${app.media.path-style-access}") boolean pathStyleAccess
	) {
		return S3Client.builder()
			.endpointOverride(endpoint)
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
			.forcePathStyle(pathStyleAccess)
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.build();
	}
}
