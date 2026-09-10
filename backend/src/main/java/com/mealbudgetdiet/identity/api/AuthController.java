package com.mealbudgetdiet.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.identity.application.SessionAuthenticationService;
import com.mealbudgetdiet.identity.application.SessionLogoutService;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.ledger.application.OnboardingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final OnboardingService onboardingService;
	private final SessionAuthenticationService sessionAuthenticationService;
	private final SessionLogoutService sessionLogoutService;
	private final IdentityService identityService;

	public AuthController(
		OnboardingService onboardingService,
		SessionAuthenticationService sessionAuthenticationService,
		SessionLogoutService sessionLogoutService,
		IdentityService identityService
	) {
		this.onboardingService = onboardingService;
		this.sessionAuthenticationService = sessionAuthenticationService;
		this.sessionLogoutService = sessionLogoutService;
		this.identityService = identityService;
	}

	@GetMapping("/csrf")
	CsrfResponse csrf(CsrfToken csrfToken) {
		return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
	}

	@PostMapping("/register")
	ResponseEntity<AuthUserResponse> register(
		@Valid @RequestBody RegisterRequest requestBody,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		onboardingService.register(
			requestBody.inviteCode(),
			requestBody.email(),
			requestBody.password(),
			requestBody.displayName()
		);
		var principal = sessionAuthenticationService.login(
			requestBody.email(), requestBody.password(), request, response);
		return ResponseEntity.status(HttpStatus.CREATED).body(response(principal));
	}

	@PostMapping("/login")
	AuthUserResponse login(
		@Valid @RequestBody LoginRequest requestBody,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		return response(sessionAuthenticationService.login(
			requestBody.email(), requestBody.password(), request, response));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		sessionLogoutService.logout(request, response);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	AuthUserResponse me(@AuthenticationPrincipal MealBudgetPrincipal principal) {
		return response(principal);
	}

	private AuthUserResponse response(MealBudgetPrincipal principal) {
		return AuthUserResponse.from(principal, identityService.getUser(principal.id()).getProfileImageId());
	}

	private record CsrfResponse(String headerName, String parameterName, String token) {
	}
}
