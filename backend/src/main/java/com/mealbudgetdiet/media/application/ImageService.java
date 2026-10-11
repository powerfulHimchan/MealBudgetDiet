package com.mealbudgetdiet.media.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mealbudgetdiet.identity.infrastructure.UserRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.media.domain.ExpenseImage;
import com.mealbudgetdiet.media.domain.ImageAsset;
import com.mealbudgetdiet.media.domain.ImagePurpose;
import com.mealbudgetdiet.media.domain.ImageStatus;
import com.mealbudgetdiet.media.infrastructure.ExpenseImageRepository;
import com.mealbudgetdiet.media.infrastructure.ImageAssetRepository;
import com.mealbudgetdiet.media.infrastructure.ImageObjectStore;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class ImageService {
	private static final Logger log = LoggerFactory.getLogger(ImageService.class);

	private final LedgerAccessService ledgerAccessService;
	private final UserRepository userRepository;
	private final ImageAssetRepository imageRepository;
	private final ExpenseImageRepository expenseImageRepository;
	private final ImageObjectStore objectStore;
	private final ImageProcessor imageProcessor;
	private final Clock clock;
	private final Duration tempRetention;
	private final TransactionTemplate readTransaction;
	private final TransactionTemplate writeTransaction;

	public ImageService(
		LedgerAccessService ledgerAccessService,
		UserRepository userRepository,
		ImageAssetRepository imageRepository,
		ExpenseImageRepository expenseImageRepository,
		ImageObjectStore objectStore,
		ImageProcessor imageProcessor,
		Clock clock,
		PlatformTransactionManager transactionManager,
		@Value("${app.media.temp-retention:24h}") Duration tempRetention
	) {
		this.ledgerAccessService = ledgerAccessService;
		this.userRepository = userRepository;
		this.imageRepository = imageRepository;
		this.expenseImageRepository = expenseImageRepository;
		this.objectStore = objectStore;
		this.imageProcessor = imageProcessor;
		this.clock = clock;
		this.tempRetention = tempRetention;
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setReadOnly(true);
		this.writeTransaction = new TransactionTemplate(transactionManager);
	}

	// Run storage I/O and image processing outside image metadata transactions.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImageSnapshot upload(UUID userId, ImagePurpose purpose, MultipartFile file) {
		UUID ledgerId = readTransaction.execute(status ->
			ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId());
		if (file.getSize() > ImageProcessor.MAX_UPLOAD_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "이미지는 파일당 5MB 이하여야 합니다.");
		}
		byte[] source;
		try {
			source = file.getBytes();
		}
		catch (IOException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_READ_FAILED", "이미지 파일을 읽지 못했습니다.");
		}
		var variants = imageProcessor.processWithThumbnail(source, purpose);
		ProcessedImage processed = variants.original();
		ProcessedImage thumbnail = variants.thumbnail();
		String key = "ledgers/%s/%s.webp".formatted(ledgerId, UUID.randomUUID());
		String thumbnailKey = key.replace(".webp", "-thumbnail.webp");
		try {
			objectStore.put(key, processed.content(), "image/webp");
			objectStore.put(thumbnailKey, thumbnail.content(), "image/webp");
			return writeTransaction.execute(status -> {
				// Membership may have changed while the files were being processed.
				UUID currentLedgerId = ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
				if (!ledgerId.equals(currentLedgerId)) throw notFound();
				var image = new ImageAsset(ledgerId, userId, purpose, key,
					processed.content().length, processed.width(), processed.height());
				image.setThumbnailStorageKey(thumbnailKey);
				return snapshot(imageRepository.saveAndFlush(image));
			});
		}
		catch (RuntimeException | Error exception) {
			// Includes failures committing metadata; cleanup runs after the transaction has ended.
			safeDeleteObject(thumbnailKey);
			safeDeleteObject(key);
			throw exception;
		}
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImageContent content(UUID userId, UUID imageId) {
		return content(userId, imageId, false);
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImageContent content(UUID userId, UUID imageId, boolean thumbnail) {
		ImageAccess access = readTransaction.execute(status -> {
			ImageAsset image = requireImage(imageId);
			requireImageAccess(userId, image);
			return new ImageAccess(image.getStorageKey(), image.getThumbnailStorageKey(), image.getMimeType());
		});
		if (thumbnail && access.thumbnailKey() == null) {
			return createLegacyThumbnail(userId, imageId, access);
		}
		String key = thumbnail ? access.thumbnailKey() : access.key();
		byte[] bytes = objectStore.get(key);
		if (bytes == null) throw notFound();
		return new ImageContent(bytes, access.mimeType());
	}

	private ImageContent createLegacyThumbnail(UUID userId, UUID imageId, ImageAccess access) {
		byte[] original = objectStore.get(access.key());
		if (original == null) throw notFound();
		ProcessedImage thumbnail = imageProcessor.thumbnail(original);
		String key = "ledgers/thumbnails/%s/%s.webp".formatted(imageId, UUID.randomUUID());
		String savedKey;
		try {
			objectStore.put(key, thumbnail.content(), "image/webp");
			savedKey = writeTransaction.execute(status -> {
				ImageAsset image = imageRepository.findByIdForUpdate(imageId).orElseThrow(this::notFound);
				requireImageAccess(userId, image);
				// Concurrent requests/instances may already have generated the thumbnail.
				if (image.getThumbnailStorageKey() == null) image.setThumbnailStorageKey(key);
				return image.getThumbnailStorageKey();
			});
		}
		catch (RuntimeException | Error exception) {
			safeDeleteObject(key);
			throw exception;
		}
		if (key.equals(savedKey)) return new ImageContent(thumbnail.content(), "image/webp");
		safeDeleteObject(key);
		byte[] bytes = objectStore.get(savedKey);
		if (bytes == null) throw notFound();
		return new ImageContent(bytes, "image/webp");
	}

	private void requireImageAccess(UUID userId, ImageAsset image) {
		if (image.getStatus() == ImageStatus.TEMP) {
			if (!userId.equals(image.getUploadedByUserId())) throw notFound();
		} else {
			UUID ledgerId = ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
			if (!ledgerId.equals(image.getLedgerId())) throw notFound();
		}
	}

	private record ImageAccess(String key, String thumbnailKey, String mimeType) { }

	@Transactional
	public void cancelTemporary(UUID userId, UUID imageId) {
		ImageAsset image = requireImageForUpdate(imageId);
		if (image.getStatus() != ImageStatus.TEMP || !userId.equals(image.getUploadedByUserId())) throw notFound();
		imageRepository.delete(image);
		imageRepository.flush();
		deleteImageObjectsAfterCommit(image);
	}

	@Transactional(readOnly = true)
	public List<ExpenseImageSnapshot> expenseImages(UUID expenseId) {
		return expenseImages(List.of(expenseId)).getOrDefault(expenseId, List.of());
	}

	@Transactional(readOnly = true)
	public Map<UUID, List<ExpenseImageSnapshot>> expenseImages(List<UUID> expenseIds) {
		if (expenseIds.isEmpty()) return Map.of();
		// Link rows contain everything needed by the response; no ImageAsset lookup is necessary.
		return expenseImageRepository.findAllByExpenseIdInOrderByExpenseIdAscSortOrderAsc(expenseIds)
			.stream().collect(Collectors.groupingBy(ExpenseImage::getExpenseId,
				Collectors.mapping(link -> new ExpenseImageSnapshot(link.getImageId(),
					contentUrl(link.getImageId()), thumbnailUrl(link.getImageId()), link.getSortOrder()),
					Collectors.toList())));
	}

	@Transactional
	public void attachExpenseImages(UUID userId, UUID ledgerId, UUID expenseId, List<UUID> requestedIds) {
		List<UUID> imageIds = requestedIds == null ? List.of() : List.copyOf(requestedIds);
		validateImageIds(imageIds);
		List<ExpenseImage> existingLinks = expenseImageRepository.findAllByExpenseIdOrderBySortOrder(expenseId);
		var existingIds = existingLinks.stream().map(ExpenseImage::getImageId).collect(Collectors.toSet());
		Map<UUID, ImageAsset> requested = imageRepository.findAllByIdForUpdate(imageIds).stream()
			.collect(Collectors.toMap(ImageAsset::getId, Function.identity()));
		if (requested.size() != imageIds.size()) throw invalidExpenseImage();

		for (UUID imageId : imageIds) {
			ImageAsset image = requested.get(imageId);
			if (!ledgerId.equals(image.getLedgerId()) || image.getPurpose() != ImagePurpose.EXPENSE) {
				throw invalidExpenseImage();
			}
			if (image.getStatus() == ImageStatus.TEMP && !userId.equals(image.getUploadedByUserId())) {
				throw invalidExpenseImage();
			}
			if (image.getStatus() == ImageStatus.ACTIVE && !existingIds.contains(imageId)) {
				throw invalidExpenseImage();
			}
		}

		expenseImageRepository.deleteAll(existingLinks);
		expenseImageRepository.flush();
		List<ImageAsset> removed = imageRepository.findAllByIdForUpdate(existingIds.stream()
			.filter(id -> !requested.containsKey(id)).toList());
		for (ImageAsset image : removed) {
			imageRepository.delete(image);
			deleteImageObjectsAfterCommit(image);
		}
		Instant now = clock.instant();
		List<ExpenseImage> nextLinks = new ArrayList<>();
		for (int index = 0; index < imageIds.size(); index++) {
			ImageAsset image = requested.get(imageIds.get(index));
			if (image.getStatus() == ImageStatus.TEMP) image.activate(now);
			nextLinks.add(new ExpenseImage(expenseId, image.getId(), index));
		}
		expenseImageRepository.saveAll(nextLinks);
	}

	@Transactional
	public void deleteExpenseImages(UUID expenseId) {
		List<ExpenseImage> links = expenseImageRepository.findAllByExpenseIdOrderBySortOrder(expenseId);
		List<ImageAsset> images = imageRepository.findAllByIdForUpdate(links.stream().map(ExpenseImage::getImageId).toList());
		expenseImageRepository.deleteAll(links);
		expenseImageRepository.flush();
		imageRepository.deleteAll(images);
		for (ImageAsset image : images) deleteImageObjectsAfterCommit(image);
	}

	@Transactional
	public String replaceProfileImage(UUID userId, UUID imageId) {
		UUID ledgerId = ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
		var user = userRepository.findById(userId).orElseThrow(this::notFound);
		ImageAsset image = requireImageForUpdate(imageId);
		if (!ledgerId.equals(image.getLedgerId()) || image.getPurpose() != ImagePurpose.PROFILE
			|| image.getStatus() != ImageStatus.TEMP || !userId.equals(image.getUploadedByUserId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_INVALID", "프로필 사진으로 사용할 수 없는 이미지입니다.");
		}
		UUID previousId = user.getProfileImageId();
		image.activate(clock.instant());
		user.changeProfileImage(imageId);
		userRepository.flush();
		deleteReplacedProfile(previousId);
		return contentUrl(imageId);
	}

	@Transactional
	public void deleteProfileImage(UUID userId) {
		var user = userRepository.findById(userId).orElseThrow(this::notFound);
		UUID previousId = user.getProfileImageId();
		if (previousId == null) return;
		user.changeProfileImage(null);
		userRepository.flush();
		deleteReplacedProfile(previousId);
	}

	@Transactional
	public void deleteLedgerObjectsAfterCommit(UUID ledgerId) {
		for (ImageAsset image : imageRepository.findAllByLedgerId(ledgerId)) {
			deleteImageObjectsAfterCommit(image);
		}
	}

	@Transactional
	@Scheduled(fixedDelayString = "${app.media.cleanup-interval-ms:3600000}")
	public void cleanupExpiredTemporaryImages() {
		List<ImageAsset> expired = imageRepository.findAllByStatusAndCreatedAtBefore(
			ImageStatus.TEMP, clock.instant().minus(tempRetention));
		imageRepository.deleteAll(expired);
		for (ImageAsset image : expired) deleteImageObjectsAfterCommit(image);
	}

	public static String contentUrl(UUID imageId) {
		return "/api/v1/images/" + imageId + "/content";
	}

	public static String thumbnailUrl(UUID imageId) {
		return "/api/v1/images/" + imageId + "/thumbnail";
	}

	private void deleteImageObjectsAfterCommit(ImageAsset image) {
		deleteObjectAfterCommit(image.getStorageKey());
		if (image.getThumbnailStorageKey() != null) deleteObjectAfterCommit(image.getThumbnailStorageKey());
	}

	private void deleteReplacedProfile(UUID previousId) {
		if (previousId == null) return;
		imageRepository.findByIdForUpdate(previousId).ifPresent(previous -> {
			imageRepository.delete(previous);
			deleteImageObjectsAfterCommit(previous);
		});
	}

	private static void validateImageIds(List<UUID> ids) {
		if (ids.size() > 3) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_EXPENSE_IMAGES", "식비에는 이미지를 최대 3장까지 등록할 수 있습니다.");
		}
		if (new LinkedHashSet<>(ids).size() != ids.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_EXPENSE_IMAGE", "같은 이미지를 중복으로 등록할 수 없습니다.");
		}
	}

	private ImageAsset requireImage(UUID imageId) {
		return imageRepository.findById(imageId).orElseThrow(this::notFound);
	}

	private ImageAsset requireImageForUpdate(UUID imageId) {
		return imageRepository.findByIdForUpdate(imageId).orElseThrow(this::notFound);
	}

	private static ImageSnapshot snapshot(ImageAsset image) {
		return new ImageSnapshot(
			image.getId(), image.getPurpose(), contentUrl(image.getId()), thumbnailUrl(image.getId()), image.getMimeType(),
			image.getWidth(), image.getHeight(), image.getStatus());
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "이미지를 찾을 수 없습니다.");
	}

	private static ApiException invalidExpenseImage() {
		return new ApiException(HttpStatus.BAD_REQUEST, "EXPENSE_IMAGE_INVALID", "식비에 연결할 수 없는 이미지가 포함되어 있습니다.");
	}

	private void deleteObjectAfterCommit(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			safeDeleteObject(key);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCommit() { safeDeleteObject(key); }
		});
	}

	private void safeDeleteObject(String key) {
		try {
			objectStore.delete(key);
		}
		catch (RuntimeException exception) {
			log.error("Failed to delete image object {}", key, exception);
		}
	}
}
