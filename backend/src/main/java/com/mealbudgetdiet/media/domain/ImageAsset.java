package com.mealbudgetdiet.media.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "images")
public class ImageAsset {

	@Id
	private UUID id;

	@Column(name = "ledger_id", nullable = false)
	private UUID ledgerId;

	@Column(name = "uploaded_by_user_id")
	private UUID uploadedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImagePurpose purpose;

	@Column(name = "storage_key", nullable = false, length = 500, unique = true)
	private String storageKey;

	@Column(name = "mime_type", nullable = false, length = 50)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImageStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "activated_at")
	private Instant activatedAt;

	protected ImageAsset() {
	}

	public ImageAsset(
		UUID ledgerId,
		UUID uploadedByUserId,
		ImagePurpose purpose,
		String storageKey,
		long sizeBytes,
		int width,
		int height
	) {
		this.id = UUID.randomUUID();
		this.ledgerId = ledgerId;
		this.uploadedByUserId = uploadedByUserId;
		this.purpose = purpose;
		this.storageKey = storageKey;
		this.mimeType = "image/webp";
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
		this.status = ImageStatus.TEMP;
	}

	public void activate(Instant activatedAt) {
		this.status = ImageStatus.ACTIVE;
		this.activatedAt = activatedAt;
	}

	public UUID getId() { return id; }
	public UUID getLedgerId() { return ledgerId; }
	public UUID getUploadedByUserId() { return uploadedByUserId; }
	public ImagePurpose getPurpose() { return purpose; }
	public String getStorageKey() { return storageKey; }
	public String getMimeType() { return mimeType; }
	public long getSizeBytes() { return sizeBytes; }
	public int getWidth() { return width; }
	public int getHeight() { return height; }
	public ImageStatus getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
}
