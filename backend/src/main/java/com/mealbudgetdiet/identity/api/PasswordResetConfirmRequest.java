package com.mealbudgetdiet.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
	@NotBlank(message = "재설정 토큰이 필요합니다.")
	@Size(max = 512, message = "재설정 토큰이 너무 깁니다.")
	String token,

	@NotBlank(message = "새 비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 72, message = "새 비밀번호는 8자 이상 72자 이하여야 합니다.")
	String newPassword
) {
}
