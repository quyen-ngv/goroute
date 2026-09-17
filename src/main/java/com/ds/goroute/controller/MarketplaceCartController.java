package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AddCartItemRequest;
import com.ds.goroute.dto.response.CartResponse;
import com.ds.goroute.service.MarketplaceCartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/marketplace-cart")
@RequiredArgsConstructor
public class MarketplaceCartController {
    private final MarketplaceCartService service;

    @GetMapping
    public ResponseEntity<BaseResponse<CartResponse>> get(Authentication authentication) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getCart(user(authentication))));
    }

    /** Badge count only. Cheap enough for every app resume; the full cart re-quotes every line. */
    @GetMapping("/count")
    public ResponseEntity<BaseResponse<Map<String, Long>>> count(Authentication authentication) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(Map.of("count", service.countItems(user(authentication)))));
    }

    @PostMapping("/items")
    public ResponseEntity<BaseResponse<CartResponse>> add(Authentication authentication,
                                                          @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.addItem(user(authentication), request)));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<BaseResponse<CartResponse>> remove(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.removeItem(user(authentication), id)));
    }

    @DeleteMapping
    public ResponseEntity<BaseResponse<CartResponse>> clear(Authentication authentication) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.clear(user(authentication))));
    }

    private UUID user(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID id ? id : UUID.fromString(principal.toString());
    }
}
