package com.mealbudgetdiet.identity.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.application.AuthRateLimitException;
import com.mealbudgetdiet.identity.application.AuthRateLimitService;
import com.mealbudgetdiet.identity.application.AuthSecurityAuditLogger;
import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.identity.application.PasswordResetService;
import com.mealbudgetdiet.identity.application.SessionAuthenticationService;
import com.mealbudgetdiet.identity.application.SessionInvalidationService;
import com.mealbudgetdiet.identity.application.SessionLogoutService;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.ledger.application.OnboardingService;
import com.mealbudgetdiet.shared.api.ApiException;

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
	private final PasswordResetService passwordResetService;
	private final SessionInvalidationService sessionInvalidationService;
	private final AuthRateLimitService authRateLimitService;
	private final AuthSecurityAuditLogger auditLogger;

	public AuthController(
		OnboardingService onboardingService,
		SessionAuthenticationService sessionAuthenticationService,
		SessionLogoutService sessionLogoutService,
		IdentityService identityService,
		PasswordResetService passwordResetService,
		SessionInvalidationService sessionInvalidationService,
		AuthRateLimitService authRateLimitService,
		AuthSecurityAuditLogger auditLogger
	) {
		this.onboardingService = onboardingService;
		this.sessionAuthenticationService = sessionAuthenticationService;
		this.sessionLogoutService = sessionLogoutService;
		this.identityService = identityService;
		this.passwordResetService = passwordResetService;
		this.sessionInvalidationService = sessionInvalidationService;
		this.authRateLimitService = authRateLimitService;
		this.auditLogger = auditLogger;
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
		String clientAddress = request.getRemoteAddr();
		try {
			authRateLimitService.acquireLoginAttempt(requestBody.email(), clientAddress);
		}
		catch (AuthRateLimitException exception) {
			auditLogger.loginRateLimited(requestBody.email(), clientAddress);
			throw exception;
		}
		MealBudgetPrincipal principal;
		try {
			principal = sessionAuthenticationService.login(
				requestBody.email(), requestBody.password(), request, response);
		}
		catch (ApiException exception) {
			auditLogger.loginFailed(requestBody.email(), clientAddress);
			throw exception;
		}
		authRateLimitService.clearLoginFailures(requestBody.email(), clientAddress);
		auditLogger.loginSucceeded(requestBody.email(), clientAddress);
		return response(principal);
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		sessionLogoutService.logout(request, response);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password-reset-requests")
	ResponseEntity<Void> requestPasswordReset(
		@Valid @RequestBody PasswordResetRequest requestBody,
		HttpServletRequest request
	) {
		String clientAddress = request.getRemoteAddr();
		boolean allowed = authRateLimitService.acquirePasswordResetRequest(
			requestBody.email(), clientAddress);
		if (allowed) {
			passwordResetService.requestReset(requestBody.email());
		}
		auditLogger.passwordResetRequested(requestBody.email(), clientAddress, allowed);
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password-resets")
	ResponseEntity<Void> resetPassword(
		@Valid @RequestBody PasswordResetConfirmRequest requestBody,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		String clientAddress = request.getRemoteAddr();
		try {
			String email = passwordResetService.resetPassword(requestBody.token(), requestBody.newPassword());
			sessionInvalidationService.invalidateByEmails(List.of(email));
			sessionLogoutService.logout(request, response);
			auditLogger.passwordResetSucceeded(email, clientAddress);
			return ResponseEntity.noContent().build();
		}
		catch (ApiException exception) {
			auditLogger.passwordResetFailed(clientAddress, exception.getCode());
			throw exception;
		}
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
