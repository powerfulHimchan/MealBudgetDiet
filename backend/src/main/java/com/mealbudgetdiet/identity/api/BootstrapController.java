package com.mealbudgetdiet.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.application.SessionAuthenticationService;
import com.mealbudgetdiet.ledger.application.OnboardingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {

	private final OnboardingService onboardingService;
	private final SessionAuthenticationService sessionAuthenticationService;

	public BootstrapController(
		OnboardingService onboardingService,
		SessionAuthenticationService sessionAuthenticationService
	) {
		this.onboardingService = onboardingService;
		this.sessionAuthenticationService = sessionAuthenticationService;
	}

	@GetMapping("/status")
	BootstrapStatusResponse status() {
		return new BootstrapStatusResponse(onboardingService.isBootstrapAvailable());
	}

	@PostMapping("/admin")
	ResponseEntity<AuthUserResponse> createAdmin(
		@RequestHeader(name = "X-Bootstrap-Token", required = false) String bootstrapToken,
		@Valid @RequestBody BootstrapAdminRequest requestBody,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		onboardingService.bootstrapAdmin(
			bootstrapToken,
			requestBody.email(),
			requestBody.password(),
			requestBody.displayName(),
			requestBody.ledgerName(),
			requestBody.defaultMonthlyBudget()
		);
		var principal = sessionAuthenticationService.login(
			requestBody.email(), requestBody.password(), request, response);
		return ResponseEntity.status(HttpStatus.CREATED).body(AuthUserResponse.from(principal));
	}

	private record BootstrapStatusResponse(boolean available) {
	}
}
