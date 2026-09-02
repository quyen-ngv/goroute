package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateActivityOrderRequest;
import com.ds.goroute.dto.response.ActivityOrderResponse;
import com.ds.goroute.dto.request.BookingChangeRequests;
import com.ds.goroute.dto.response.BookingChangeRequestResponse;
import com.ds.goroute.dto.response.CancellationPreviewResponse;
import com.ds.goroute.service.BookingChangeRequestService;
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
    private final BookingChangeRequestService changeRequests;

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

    @PostMapping("/{id}/change-requests")
public ResponseEntity<BaseResponse<BookingChangeRequestResponse>> requestChange(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody BookingChangeRequests.CreateActivityChange r) {
return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(changeRequests.requestActivityChange(user(authentication), id, r)));
}

@GetMapping("/{id}/change-requests")
public ResponseEntity<BaseResponse<List<BookingChangeRequestResponse>>> changeRequests(Authentication authentication, @PathVariable UUID id) {
return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.listForActivityOrder(user(authentication), id, false)));
}

@PostMapping("/{id}/change-requests/{requestId}/withdraw")
public ResponseEntity<BaseResponse<BookingChangeRequestResponse>> withdrawChange(Authentication authentication, @PathVariable UUID id, @PathVariable UUID requestId) {
return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.withdraw(user(authentication), requestId)));
}

@GetMapping("/{id}/cancellation-preview")
public ResponseEntity<BaseResponse<CancellationPreviewResponse>> cancellationPreview(Authentication authentication, @PathVariable UUID id) {
return ResponseEntity.ok(BaseResponse.ofSucceeded(service.previewMyCancellation(user(authentication), id)));
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
