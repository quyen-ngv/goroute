package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.IcalFeedLinkResponse;
import com.ds.goroute.service.IcalFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Partner-side management of a room type's iCal subscription link (authorization lives in the service). */
@RestController
@RequestMapping("/v1/api/partner/hotels/rooms/{roomId}/ical-feed")
@RequiredArgsConstructor
public class PartnerIcalController {

    private final IcalFeedService service;

    @GetMapping
    public ResponseEntity<BaseResponse<IcalFeedLinkResponse>> feedLink(Authentication a, @PathVariable UUID roomId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getFeedLink(user(a), roomId)));
    }

    @PostMapping("/rotate")
    public ResponseEntity<BaseResponse<IcalFeedLinkResponse>> rotate(Authentication a, @PathVariable UUID roomId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.rotateFeedLink(user(a), roomId)));
    }

    private UUID user(Authentication a) {
        if (a == null || a.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object p = a.getPrincipal();
        return p instanceof UUID id ? id : UUID.fromString(p.toString());
    }
}
