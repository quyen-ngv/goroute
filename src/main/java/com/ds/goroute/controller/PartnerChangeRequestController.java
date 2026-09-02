package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.BookingChangeRequestResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.BookingChangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Organization-wide inbox of guest change requests for the partner console. */
@RestController
@RequestMapping("/v1/api/partner/organizations/{organizationId}/change-requests")
@RequiredArgsConstructor
public class PartnerChangeRequestController {
    private final BookingChangeRequestService service;

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<BookingChangeRequestResponse>>> list(Authentication auth, @PathVariable UUID organizationId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listForOrganization(userId(auth), organizationId, status, page, size)));
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID uuid ? uuid : UUID.fromString(principal.toString());
    }
}
