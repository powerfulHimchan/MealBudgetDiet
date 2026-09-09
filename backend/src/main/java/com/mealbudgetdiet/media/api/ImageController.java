package com.mealbudgetdiet.media.api;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.media.application.ImageService;
import com.mealbudgetdiet.media.application.ImageSnapshot;
import com.mealbudgetdiet.media.domain.ImagePurpose;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1")
public class ImageController {

	private final ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping(path = "/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ImageSnapshot> upload(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam ImagePurpose purpose,
		@RequestPart("file") MultipartFile file
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(imageService.upload(principal.id(), purpose, file));
	}

	@GetMapping("/images/{imageId}/content")
	ResponseEntity<byte[]> content(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID imageId
	) {
		var image = imageService.content(principal.id(), imageId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.mimeType()))
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff")
			.body(image.content());
	}

	@DeleteMapping("/uploads/images/{imageId}")
	ResponseEntity<Void> cancel(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID imageId
	) {
		imageService.cancelTemporary(principal.id(), imageId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/account/profile-image")
	ProfileImageResponse replaceProfile(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody ProfileImageRequest request
	) {
		return new ProfileImageResponse(imageService.replaceProfileImage(principal.id(), request.imageId()));
	}

	@DeleteMapping("/account/profile-image")
	ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal MealBudgetPrincipal principal) {
		imageService.deleteProfileImage(principal.id());
		return ResponseEntity.noContent().build();
	}

	public record ProfileImageRequest(@NotNull UUID imageId) {
	}

	public record ProfileImageResponse(String profileImageUrl) {
	}
}
