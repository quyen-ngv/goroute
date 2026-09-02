package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.GrantSubscriptionRequest;
import com.ds.goroute.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Operator view of a user's plan.
 *
 * <p>Reading and granting are separate permissions on purpose: support needs to answer "when does
 * my Pro end" far more often than anybody needs to hand out a year of it.
 */
@RestController
@RequestMapping("/v1/api/admin/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController extends BaseController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    @PreAuthorize("@adminAuthorization.can(authentication,'subscriptions','get')")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(ofSucceeded(subscriptionService.plans(false)));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'subscriptions','get')")
    public ResponseEntity<?> forUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(Map.of(
                "subscription", subscriptionService.summary(userId),
                "history", subscriptionService.history(userId, 50))));
    }

    @PostMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'subscriptions','update')")
    public ResponseEntity<?> grant(@PathVariable UUID userId,
                                   @Valid @RequestBody GrantSubscriptionRequest request,
                                   @CurrentUser UUID operatorId) {
        return ResponseEntity.ok(ofSucceeded(subscriptionService.grant(
                userId, request.getPlanCode(), operatorId,
                request.getReferenceKey(), request.getNote())));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'subscriptions','update')")
    public ResponseEntity<?> revoke(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(subscriptionService.revoke(userId)));
    }
}
