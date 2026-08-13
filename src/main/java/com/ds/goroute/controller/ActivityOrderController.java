package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateActivityOrderRequest;
import com.ds.goroute.dto.response.ActivityOrderResponse;
import com.ds.goroute.service.ActivityCommerceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/activity-orders")
@RequiredArgsConstructor
public class ActivityOrderController {
    private final ActivityCommerceService service;

    @PostMapping
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateActivityOrderRequest request) {
        if ((request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) && idempotencyKey != null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(service.createOrder(user(authentication), request)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<ActivityOrderResponse>>> list(Authentication authentication,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMyOrders(user(authentication), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> get(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getMyOrder(user(authentication), id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> cancel(Authentication authentication, @PathVariable UUID id,
                                                                         @RequestParam(required = false) String reason,
                                                                         @RequestParam(required = false) Long expectedVersion) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.cancelMyOrder(user(authentication), id, reason, expectedVersion)));
    }

    private UUID user(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID id ? id : UUID.fromString(principal.toString());
    }
}
