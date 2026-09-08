package com.mealbudgetdiet.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	@Size(max = 320, message = "이메일은 320자 이하여야 합니다.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(max = 72, message = "비밀번호는 72자 이하여야 합니다.")
	String password
) {
	public LoginRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}
