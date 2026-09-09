package com.mealbudgetdiet.media.application;

import java.util.UUID;

import com.mealbudgetdiet.media.domain.ImagePurpose;
import com.mealbudgetdiet.media.domain.ImageStatus;

public record ImageSnapshot(
	UUID id,
	ImagePurpose purpose,
	String contentUrl,
	String mimeType,
	int width,
	int height,
	ImageStatus status
) {
}
