package com.mealbudgetdiet.notification.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, columnDefinition = "text")
	private String endpoint;

	@Column(name = "p256dh_key", nullable = false, columnDefinition = "text")
	private String p256dhKey;

	@Column(name = "auth_key", nullable = false, columnDefinition = "text")
	private String authKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PushSubscriptionStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PushSubscription() {
	}

	public PushSubscription(UUID userId, String endpoint, String p256dhKey, String authKey) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.endpoint = endpoint;
		this.p256dhKey = p256dhKey;
		this.authKey = authKey;
		this.status = PushSubscriptionStatus.ACTIVE;
	}

	public void activateFor(UUID userId, String p256dhKey, String authKey) {
		this.userId = userId;
		this.p256dhKey = p256dhKey;
		this.authKey = authKey;
		this.status = PushSubscriptionStatus.ACTIVE;
	}

	public void disable() {
		this.status = PushSubscriptionStatus.DISABLED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public PushSubscriptionStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
