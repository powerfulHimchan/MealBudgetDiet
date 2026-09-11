package com.mealbudgetdiet.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LedgerRegistrationRequest(
	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	@Size(max = 320, message = "이메일은 320자 이하여야 합니다.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
	String password,

	@NotBlank(message = "표시 이름을 입력해 주세요.")
	@Size(max = 50, message = "표시 이름은 50자 이하여야 합니다.")
	String displayName,

	@NotBlank(message = "장부 이름을 입력해 주세요.")
	@Size(max = 100, message = "장부 이름은 100자 이하여야 합니다.")
	String ledgerName,

	@Positive(message = "기본 월 예산은 0보다 커야 합니다.")
	long defaultMonthlyBudget
) {
	public LedgerRegistrationRequest {
		if (email != null) {
			email = email.trim();
		}
	}
}
