package com.mealbudgetdiet.identity.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.mealbudgetdiet.analytics.application.AnalyticsService;
import com.mealbudgetdiet.analytics.application.WidgetSummary;
import com.mealbudgetdiet.identity.application.WidgetConnectionService;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/widget")
public class WidgetConnectionController {
    private final WidgetConnectionService connections;
    private final AnalyticsService analytics;

    public WidgetConnectionController(WidgetConnectionService connections, AnalyticsService analytics) {
        this.connections = connections;
        this.analytics = analytics;
    }

    @PostMapping("/connections")
    ResponseEntity<Void> authorize(@AuthenticationPrincipal MealBudgetPrincipal principal,
        HttpServletRequest request, @Valid @RequestBody Authorize body) {
        connections.authorize(principal.id(), request.getSession().getId(), body.challenge());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/connections")
    ResponseEntity<Void> revoke(HttpServletRequest request) {
        connections.revokeSession(request.getSession().getId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/device/claim")
    ResponseEntity<Token> claim(@Valid @RequestBody Claim body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Token(connections.claim(body.verifier())));
    }

    @GetMapping("/device/summary")
    ResponseEntity<WidgetSummary> summary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(analytics.widgetSummary(connections.resolve(authorization)));
    }

    public record Authorize(@NotNull @Pattern(regexp = "[0-9a-f]{64}") String challenge) { }
    public record Claim(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String verifier) { }
    public record Token(String token) { }
}
