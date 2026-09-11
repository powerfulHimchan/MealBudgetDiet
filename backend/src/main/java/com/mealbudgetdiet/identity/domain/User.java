package com.mealbudgetdiet.identity.domain;

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
@Table(name = "users")
public class User {

	private static final String DELETED_DISPLAY_NAME = "탈퇴한 사용자";

	@Id
	private UUID id;

	@Column(nullable = false, length = 320, unique = true)
	private String email;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(name = "profile_image_id")
	private UUID profileImageId;

	@Enumerated(EnumType.STRING)
	@Column(name = "service_role", nullable = false, length = 20)
	private ServiceRole serviceRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String email, String passwordHash, String displayName, ServiceRole serviceRole) {
		this.id = UUID.randomUUID();
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.serviceRole = serviceRole;
		this.status = UserStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public ServiceRole getServiceRole() {
		return serviceRole;
	}

	public UserStatus getStatus() {
		return status;
	}

	public UUID getProfileImageId() {
		return profileImageId;
	}

	public void changeProfileImage(UUID profileImageId) {
		this.profileImageId = profileImageId;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void withdraw() {
		this.email = "deleted+" + id + "@users.invalid";
		this.displayName = DELETED_DISPLAY_NAME;
		this.status = UserStatus.WITHDRAWN;
		this.passwordHash = null;
		this.profileImageId = null;
	}

	public void reactivate(String passwordHash, String displayName, ServiceRole serviceRole) {
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.serviceRole = serviceRole;
		this.status = UserStatus.ACTIVE;
	}
}
