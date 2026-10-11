package com.mealbudgetdiet.media.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.mealbudgetdiet.media.domain.ImageAsset;
import com.mealbudgetdiet.media.domain.ImageStatus;

import jakarta.persistence.LockModeType;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select image from ImageAsset image where image.id = :id")
	Optional<ImageAsset> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select image from ImageAsset image where image.id in :ids order by image.id")
	List<ImageAsset> findAllByIdForUpdate(@Param("ids") List<UUID> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<ImageAsset> findAllByStatusAndCreatedAtBefore(ImageStatus status, Instant createdAt);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<ImageAsset> findAllByLedgerId(UUID ledgerId);
}
