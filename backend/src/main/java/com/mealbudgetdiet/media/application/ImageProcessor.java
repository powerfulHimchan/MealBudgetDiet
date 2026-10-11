package com.mealbudgetdiet.media.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.media.domain.ImagePurpose;
import com.mealbudgetdiet.shared.api.ApiException;

@Component
public class ImageProcessor {

	public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
	public static final int THUMBNAIL_DIMENSION = 240;
	private static final long MAX_SOURCE_PIXELS = 40_000_000L;

	public ProcessedImage process(byte[] source, ImagePurpose purpose) {
		return render(validatedSource(source), purpose == ImagePurpose.PROFILE ? 512 : 1600);
	}

	public ImageVariants processWithThumbnail(byte[] source, ImagePurpose purpose) {
		BufferedImage decoded = validatedSource(source);
		return new ImageVariants(render(decoded, purpose == ImagePurpose.PROFILE ? 512 : 1600),
			render(decoded, THUMBNAIL_DIMENSION));
	}

	public record ImageVariants(ProcessedImage original, ProcessedImage thumbnail) { }

	private BufferedImage validatedSource(byte[] source) {
		if (source.length == 0) {
			throw unsupported();
		}
		if (source.length > MAX_UPLOAD_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "이미지는 파일당 5MB 이하여야 합니다.");
		}
		if (!isJpeg(source) && !isPng(source) && !isWebp(source)) {
			throw unsupported();
		}

		return decode(source);
	}

	public ProcessedImage thumbnail(byte[] source) {
		// Stored originals have already passed upload validation and may encode above 5 MB.
		return render(decode(source), THUMBNAIL_DIMENSION);
	}

	private ProcessedImage render(BufferedImage decoded, int maxDimension) {
		BufferedImage rendered = resizeAndStripMetadata(decoded, maxDimension);
		return new ProcessedImage(encodeWebp(rendered), rendered.getWidth(), rendered.getHeight());
	}

	private BufferedImage decode(byte[] source) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) throw unsupported();
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_PIXELS) {
					throw unsupported();
				}
				BufferedImage decoded = reader.read(0);
				if (decoded == null) throw unsupported();
				return decoded;
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException exception) {
			if (exception instanceof ApiException apiException) throw apiException;
			throw unsupported();
		}
	}

	private BufferedImage resizeAndStripMetadata(BufferedImage source, int maxDimension) {
		double scale = Math.min(1d, (double) maxDimension / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

	private byte[] encodeWebp(BufferedImage image) {
		try (var output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, "webp", output)) {
				throw new IllegalStateException("WebP ImageIO writer is unavailable");
			}
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new IllegalStateException("WebP image encoding failed", exception);
		}
	}

	private static boolean isJpeg(byte[] source) {
		return source.length >= 3 && (source[0] & 0xff) == 0xff && (source[1] & 0xff) == 0xd8 && (source[2] & 0xff) == 0xff;
	}

	private static boolean isPng(byte[] source) {
		byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
		return source.length >= signature.length && Arrays.equals(Arrays.copyOf(source, signature.length), signature);
	}

	private static boolean isWebp(byte[] source) {
		return source.length >= 12
			&& source[0] == 'R' && source[1] == 'I' && source[2] == 'F' && source[3] == 'F'
			&& source[8] == 'W' && source[9] == 'E' && source[10] == 'B' && source[11] == 'P';
	}

	private static ApiException unsupported() {
		return new ApiException(
			HttpStatus.UNSUPPORTED_MEDIA_TYPE,
			"UNSUPPORTED_IMAGE",
			"JPEG, PNG 또는 WebP 형식의 정상적인 이미지만 등록할 수 있습니다."
		);
	}
}
