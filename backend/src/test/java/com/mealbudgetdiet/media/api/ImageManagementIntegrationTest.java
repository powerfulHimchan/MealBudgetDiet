package com.mealbudgetdiet.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.hibernate.SessionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.expense.application.ExpenseService;
import com.mealbudgetdiet.media.infrastructure.ImageObjectStore;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
@SpringBootTest
@org.springframework.context.annotation.Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"app.bootstrap-token=image-bootstrap-token",
	"spring.jpa.properties.hibernate.generate_statistics=true"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageManagementIntegrationTest {

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired ExpenseService expenseService;
	@Autowired ImageObjectStore objectStore;
	private Cookie session;
	private String categoryId;

	@BeforeAll
	void setUp() throws Exception {
		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "image-bootstrap-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"media-admin@example.com",
					  "password":"media-password!",
					  "displayName":"이미지 관리자",
					  "ledgerName":"이미지 장부",
					  "defaultMonthlyBudget":500000
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		session = sessionCookie(result.getResponse().getHeader("Set-Cookie"));
		var categories = mockMvc.perform(get("/api/v1/categories").cookie(session))
			.andExpect(status().isOk()).andReturn();
		categoryId = JsonPath.read(categories.getResponse().getContentAsString(), "$.items[0].id");
	}

	@Test
	void uploadsWebpAndAttachesUpToThreeImagesToExpense() throws Exception {
		String imageId = upload("EXPENSE", "receipt.png", pngBytes());
		var created = mockMvc.perform(post("/api/v1/expenses")
				.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"amount":18000,"spentOn":"2026-09-09","categoryId":"%s",
					 "merchant":"동네마트","memo":"영수증 첨부","imageIds":["%s"]}
					""".formatted(categoryId, imageId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.images[0].id").value(imageId))
			.andExpect(jsonPath("$.images[0].sortOrder").value(0))
			.andExpect(jsonPath("$.images[0].thumbnailUrl").value("/api/v1/images/" + imageId + "/thumbnail"))
			.andReturn();
		String contentUrl = JsonPath.read(created.getResponse().getContentAsString(), "$.images[0].contentUrl");
		mockMvc.perform(get("/api/v1/dashboard").cookie(session).param("yearMonth", "2026-09"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recentExpenses[0].imageUrl").value(contentUrl))
			.andExpect(jsonPath("$.recentExpenses[0].thumbnailUrl").value("/api/v1/images/" + imageId + "/thumbnail"));

		byte[] webp = mockMvc.perform(get(contentUrl).cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/webp"))
			.andExpect(header().string("Cache-Control", containsString("max-age=600")))
			.andExpect(header().string("Cache-Control", containsString("private")))
			.andExpect(header().string("Vary", containsString("Cookie")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(webp, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
		assertThat(new String(webp, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WEBP");
	}

	@Test
	void resizesThumbnailsAndKeepsTheOriginalForDetail() throws Exception {
		String id = upload("EXPENSE", "large.png", pngBytes(1200, 900));
		byte[] original = mockMvc.perform(get("/api/v1/images/" + id + "/content").cookie(session))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		byte[] thumbnail = mockMvc.perform(get("/api/v1/images/" + id + "/thumbnail").cookie(session))
			.andExpect(status().isOk()).andExpect(content().contentType("image/webp"))
			.andExpect(header().string("Cache-Control", containsString("private")))
			.andExpect(header().string("Vary", containsString("Cookie")))
			.andReturn().getResponse().getContentAsByteArray();
		var large = ImageIO.read(new ByteArrayInputStream(original));
		var small = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(large.getWidth()).isEqualTo(1200);
		assertThat(small.getWidth()).isEqualTo(240);
		assertThat(small.getHeight()).isEqualTo(180);
		assertThat(thumbnail.length).isLessThan(original.length);
	}

	@Test
	void generatesAndReusesThumbnailsForExistingImagesAndDeletesBothFiles() throws Exception {
		String id = upload("EXPENSE", "legacy.png", pngBytes(900, 600));
		String originalKey = jdbcTemplate.queryForObject("select storage_key from images where id = ?",
			String.class, UUID.fromString(id));
		String oldThumbnailKey = jdbcTemplate.queryForObject("select thumbnail_storage_key from images where id = ?",
			String.class, UUID.fromString(id));
		jdbcTemplate.update("update images set thumbnail_storage_key = null where id = ?", UUID.fromString(id));
		objectStore.delete(oldThumbnailKey);
		String url = "/api/v1/images/" + id + "/thumbnail";
		mockMvc.perform(get(url).cookie(session)).andExpect(status().isOk());
		String generatedKey = jdbcTemplate.queryForObject("select thumbnail_storage_key from images where id = ?",
			String.class, UUID.fromString(id));
		assertThat(generatedKey).isNotNull();
		byte[] generated = objectStore.get(generatedKey);
		byte[] second = mockMvc.perform(get(url).cookie(session)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(second).isEqualTo(generated);
		mockMvc.perform(delete("/api/v1/uploads/images/" + id).cookie(session).with(csrf()))
			.andExpect(status().isNoContent());
		assertThat(objectStore.get(originalKey)).isNull();
		assertThat(objectStore.get(generatedKey)).isNull();
		mockMvc.perform(get(url).cookie(session)).andExpect(status().isNotFound());
	}

	@Test
	void loadsImageLinksOnceForAnEntireTwentyExpensePage() throws Exception {
		UUID userId = jdbcTemplate.queryForObject("select id from users where email = 'media-admin@example.com'", UUID.class);
		for (int i = 0; i < 20; i++) {
			UUID imageId = UUID.fromString(upload("EXPENSE", "receipt.png", pngBytes()));
			expenseService.create(userId, 1000, LocalDate.of(2020, 1, 1), UUID.fromString(categoryId),
				"batch-query-test", "", java.util.List.of(imageId));
		}
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		var page = expenseService.search(userId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31),
			null, false, null, 20, null);
		assertThat(page.items()).hasSize(20).allSatisfy(expense -> assertThat(expense.images()).hasSize(1));
		assertThat(page.totalAmount()).isEqualTo(20_000);
		assertThat(page.totalCount()).isEqualTo(20);
		// Membership, expenses with categories, one query for image links, and one for both totals.
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
	}

	@Test
	void replacesAndDeletesOwnProfileImageAndExposesItToMembers() throws Exception {
		String imageId = upload("PROFILE", "profile.png", pngBytes());
		String contentUrl = "/api/v1/images/" + imageId + "/content";
		mockMvc.perform(put("/api/v1/account/profile-image")
				.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"imageId\":\"" + imageId + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileImageUrl").value(contentUrl));
		mockMvc.perform(get("/api/v1/auth/me").cookie(session))
			.andExpect(jsonPath("$.profileImageUrl").value(contentUrl));
		mockMvc.perform(get("/api/v1/members").cookie(session))
			.andExpect(jsonPath("$.items[0].profileImageUrl").value(contentUrl));

		mockMvc.perform(delete("/api/v1/account/profile-image").with(csrf()).cookie(session))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/auth/me").cookie(session))
			.andExpect(jsonPath("$.profileImageUrl").doesNotExist());
		mockMvc.perform(get(contentUrl).cookie(session)).andExpect(status().isNotFound());
	}

	@Test
	void rejectsUnsupportedAndOversizedImages() throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/images").file(
				new MockMultipartFile("file", "bad.gif", "image/gif", "not-an-image".getBytes()))
				.param("purpose", "EXPENSE").with(csrf()).cookie(session))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));

		mockMvc.perform(multipart("/api/v1/uploads/images").file(
				new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
				.param("purpose", "EXPENSE").with(csrf()).cookie(session))
			.andExpect(status().isPayloadTooLarge());
	}

	private String upload(String purpose, String filename, byte[] content) throws Exception {
		var result = mockMvc.perform(multipart("/api/v1/uploads/images")
				.file(new MockMultipartFile("file", filename, "image/png", content))
				.param("purpose", purpose).with(csrf()).cookie(session))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.mimeType").value("image/webp"))
			.andExpect(jsonPath("$.status").value("TEMP"))
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private byte[] pngBytes() throws Exception {
		return pngBytes(80, 60);
	}

	private byte[] pngBytes(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(new Color(20, 60, 120));
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		var output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private Cookie sessionCookie(String setCookieHeader) {
		assertThat(setCookieHeader).isNotNull().contains("MBD_SESSION=");
		String prefix = "MBD_SESSION=";
		int start = setCookieHeader.indexOf(prefix) + prefix.length();
		int end = setCookieHeader.indexOf(';', start);
		return new Cookie("MBD_SESSION", setCookieHeader.substring(start, end));
	}
}
