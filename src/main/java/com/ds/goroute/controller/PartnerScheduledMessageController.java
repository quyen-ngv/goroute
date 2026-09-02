package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpsertScheduledMessageRequest;
import com.ds.goroute.dto.response.ScheduledMessageResponse;
import com.ds.goroute.dto.response.ScheduledMessageRunResponse;
import com.ds.goroute.service.MarketplaceScheduledMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Automatic guest messages for the partner inbox. Every route is scoped by {@code organizationId};
 * {@code CHAT_WRITE} is checked in the service, which is also the entry point the job uses.
 */
@RestController
@RequestMapping("/v1/api/partner/conversations/scheduled-messages")
@RequiredArgsConstructor
@Validated
public class PartnerScheduledMessageController {
    private final MarketplaceScheduledMessageService service;

    @GetMapping
    public ResponseEntity<BaseResponse<List<ScheduledMessageResponse>>> list(Authentication auth,
            @RequestParam UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.list(userId(auth), organizationId)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ScheduledMessageResponse>> create(Authentication auth,
            @RequestParam UUID organizationId, @Valid @RequestBody UpsertScheduledMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.create(userId(auth), organizationId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<ScheduledMessageResponse>> update(Authentication auth,
            @RequestParam UUID organizationId, @PathVariable UUID id,
            @Valid @RequestBody UpsertScheduledMessageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.update(userId(auth), organizationId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(Authentication auth, @RequestParam UUID organizationId,
            @PathVariable UUID id) {
        service.delete(userId(auth), organizationId, id);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    /** Last delivery attempts of one rule — the answer to "why did my guest not get this". */
    @GetMapping("/{id}/runs")
    public ResponseEntity<BaseResponse<List<ScheduledMessageRunResponse>>> runs(Authentication auth,
            @RequestParam UUID organizationId, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.runs(userId(auth), organizationId, id, page, size)));
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID uuid ? uuid : UUID.fromString(principal.toString());
    }
}
