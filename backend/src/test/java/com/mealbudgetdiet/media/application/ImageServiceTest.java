package com.mealbudgetdiet.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.mealbudgetdiet.identity.infrastructure.UserRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.media.domain.ImagePurpose;
import com.mealbudgetdiet.media.infrastructure.ExpenseImageRepository;
import com.mealbudgetdiet.media.infrastructure.ImageAssetRepository;
import com.mealbudgetdiet.media.infrastructure.ImageObjectStore;

class ImageServiceTest {
	private final UUID userId = UUID.randomUUID();
	private final LedgerAccessService access = mock(LedgerAccessService.class);
	private final ImageAssetRepository images = mock(ImageAssetRepository.class);
	private final ImageObjectStore objects = mock(ImageObjectStore.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final ImageProcessor processor = mock(ImageProcessor.class);
	private final MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1});

	private ImageService service() {
		when(access.requireActiveMembership(userId)).thenReturn(
			new LedgerMember(UUID.randomUUID(), userId, MemberRole.ADMIN));
		when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
		when(processor.processWithThumbnail(any(), eq(ImagePurpose.EXPENSE))).thenReturn(
			new ImageProcessor.ImageVariants(new ProcessedImage(new byte[]{1, 2}, 1200, 900),
				new ProcessedImage(new byte[]{3}, 240, 180)));
		when(images.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		return new ImageService(access, mock(UserRepository.class), images,
			mock(ExpenseImageRepository.class), objects, processor, Clock.systemUTC(), transactions, Duration.ofHours(24));
	}

	@Test
	void cleansUpBothObjectsWhenTheMetadataCommitFails() {
		var service = service();
		doNothing().doThrow(new IllegalStateException("commit failed")).when(transactions).commit(any());
		assertThatThrownBy(() -> service.upload(userId, ImagePurpose.EXPENSE, file))
			.isInstanceOf(IllegalStateException.class).hasMessage("commit failed");
		var keys = ArgumentCaptor.forClass(String.class);
		verify(objects, times(2)).put(keys.capture(), any(), eq("image/webp"));
		for (String key : keys.getAllValues()) verify(objects).delete(key);
		assertThat(keys.getAllValues()).hasSize(2);
		verify(transactions, times(2)).commit(any());
	}

	@Test
	void cleansUpPartialUploadsWithoutSavingMetadata() {
		var service = service();
		doNothing().doThrow(new IllegalStateException("storage failed"))
			.when(objects).put(anyString(), any(), eq("image/webp"));
		assertThatThrownBy(() -> service.upload(userId, ImagePurpose.EXPENSE, file))
			.isInstanceOf(IllegalStateException.class).hasMessage("storage failed");
		verify(images, never()).saveAndFlush(any());
		verify(objects, times(2)).delete(anyString());
		verify(transactions).commit(any());
	}
}
