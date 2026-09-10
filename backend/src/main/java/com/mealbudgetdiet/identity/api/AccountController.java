package com.mealbudgetdiet.identity.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.identity.application.SessionInvalidationService;
import com.mealbudgetdiet.identity.application.SessionLogoutService;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

	private final IdentityService identityService;
	private final SessionInvalidationService sessionInvalidationService;
	private final SessionLogoutService sessionLogoutService;

	public AccountController(
		IdentityService identityService,
		SessionInvalidationService sessionInvalidationService,
		SessionLogoutService sessionLogoutService
	) {
		this.identityService = identityService;
		this.sessionInvalidationService = sessionInvalidationService;
		this.sessionLogoutService = sessionLogoutService;
	}

	@PostMapping("/password-change")
	ResponseEntity<Void> changePassword(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody PasswordChangeRequest requestBody,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		String email = identityService.changePassword(
			principal.id(), requestBody.currentPassword(), requestBody.newPassword());
		sessionInvalidationService.invalidateByEmails(List.of(email));
		sessionLogoutService.logout(request, response);
		return ResponseEntity.noContent().build();
	}
}
