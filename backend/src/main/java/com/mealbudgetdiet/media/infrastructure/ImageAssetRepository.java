package com.mealbudgetdiet.media.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mealbudgetdiet.media.domain.ImageAsset;
import com.mealbudgetdiet.media.domain.ImageStatus;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, UUID> {
	List<ImageAsset> findAllByStatusAndCreatedAtBefore(ImageStatus status, Instant createdAt);
	List<ImageAsset> findAllByLedgerId(UUID ledgerId);
}
