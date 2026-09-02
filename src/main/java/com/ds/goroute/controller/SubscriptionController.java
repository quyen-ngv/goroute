package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** What plan the signed-in account is on, and what it could move to. */
@RestController
@RequestMapping("/v1/api/me/subscription")
@RequiredArgsConstructor
public class SubscriptionController extends BaseController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<?> mine(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(subscriptionService.summary(userId)));
    }

    /** Only what is on sale: a retired plan is still honoured but must not be offered. */
    @GetMapping("/plans")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(ofSucceeded(subscriptionService.plans(true)));
    }
}
