package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpsertMessageTemplateRequest;
import com.ds.goroute.dto.response.MessageTemplateResponse;
import com.ds.goroute.service.MarketplaceMessageTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Quick replies for the partner inbox. Every route is scoped by {@code organizationId}; CHAT_WRITE is checked in the service. */
@RestController
@RequestMapping("/v1/api/partner/conversations/templates")
@RequiredArgsConstructor
public class PartnerMessageTemplateController {
    private final MarketplaceMessageTemplateService service;

    @GetMapping
    public ResponseEntity<BaseResponse<List<MessageTemplateResponse>>> list(Authentication auth, @RequestParam UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.list(userId(auth), organizationId)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<MessageTemplateResponse>> create(Authentication auth, @RequestParam UUID organizationId,
            @Valid @RequestBody UpsertMessageTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.create(userId(auth), organizationId, request)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<BaseResponse<MessageTemplateResponse>> update(Authentication auth, @RequestParam UUID organizationId,
            @PathVariable UUID templateId, @Valid @RequestBody UpsertMessageTemplateRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.update(userId(auth), organizationId, templateId, request)));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<BaseResponse<Void>> delete(Authentication auth, @RequestParam UUID organizationId,
            @PathVariable UUID templateId) {
        service.delete(userId(auth), organizationId, templateId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID uuid ? uuid : UUID.fromString(principal.toString());
    }
}
