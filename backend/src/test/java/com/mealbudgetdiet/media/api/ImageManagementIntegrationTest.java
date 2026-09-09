package com.mealbudgetdiet.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

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

import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
@SpringBootTest
@org.springframework.context.annotation.Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.bootstrap-token=test-bootstrap-token")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageManagementIntegrationTest {

	@Autowired MockMvc mockMvc;
	private Cookie session;
	private String categoryId;

	@BeforeAll
	void setUp() throws Exception {
		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "test-bootstrap-token")
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
			.andReturn();
		String contentUrl = JsonPath.read(created.getResponse().getContentAsString(), "$.images[0].contentUrl");

		byte[] webp = mockMvc.perform(get(contentUrl).cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/webp"))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(webp, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
		assertThat(new String(webp, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WEBP");
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
		BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(new Color(20, 60, 120));
		graphics.fillRect(0, 0, 80, 60);
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
