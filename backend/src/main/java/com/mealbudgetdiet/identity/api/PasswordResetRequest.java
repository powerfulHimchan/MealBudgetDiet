package com.mealbudgetdiet.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	@Size(max = 320, message = "이메일은 320자 이하여야 합니다.")
	String email
) {
	public PasswordResetRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}
