package com.mealbudgetdiet.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
	@NotBlank(message = "초대 코드를 입력해 주세요.")
	@Size(max = 80, message = "초대 코드가 너무 깁니다.")
	String inviteCode,

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	@Size(max = 320, message = "이메일은 320자 이하여야 합니다.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
	String password,

	@NotBlank(message = "표시 이름을 입력해 주세요.")
	@Size(max = 50, message = "표시 이름은 50자 이하여야 합니다.")
	String displayName
) {
	public RegisterRequest {
		if (inviteCode != null) {
			inviteCode = inviteCode.trim();
		}
		if (email != null) {
			email = email.trim();
		}
	}
}
