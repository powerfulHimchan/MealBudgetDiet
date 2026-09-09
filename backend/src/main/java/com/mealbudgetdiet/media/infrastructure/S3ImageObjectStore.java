package com.mealbudgetdiet.media.infrastructure;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3ImageObjectStore implements ImageObjectStore {

	private final S3Client s3Client;
	private final String bucket;
	private final AtomicBoolean bucketReady = new AtomicBoolean();

	public S3ImageObjectStore(S3Client s3Client, @Value("${app.media.bucket}") String bucket) {
		this.s3Client = s3Client;
		this.bucket = bucket;
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		ensureBucket();
		s3Client.putObject(
			PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
			RequestBody.fromBytes(content)
		);
	}

	@Override
	public byte[] get(String key) {
		try {
			return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
		}
		catch (NoSuchKeyException exception) {
			return null;
		}
		catch (S3Exception exception) {
			if (exception.statusCode() == 404) return null;
			throw exception;
		}
	}

	@Override
	public void delete(String key) {
		try {
			s3Client.deleteObject(builder -> builder.bucket(bucket).key(key));
		}
		catch (S3Exception exception) {
			if (exception.statusCode() != 404) throw exception;
		}
	}

	private void ensureBucket() {
		if (bucketReady.get()) return;
		synchronized (bucketReady) {
			if (bucketReady.get()) return;
			try {
				s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
			}
			catch (S3Exception exception) {
				if (exception.statusCode() != 404 && exception.statusCode() != 400) throw exception;
				try {
					s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
				}
				catch (S3Exception createException) {
					if (createException.statusCode() != 409) throw createException;
				}
			}
			bucketReady.set(true);
		}
	}
}
