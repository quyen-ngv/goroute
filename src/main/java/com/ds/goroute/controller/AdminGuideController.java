package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.GrantGuideRequest;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.UserGuideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Promoting an account to VietdeGuide, and taking it back.
 *
 * <p>Reading is a separate permission from granting: support answers "is this person a guide" far
 * more often than anybody needs to make one.
 */
@RestController
@RequestMapping("/v1/api/admin/guides")
@RequiredArgsConstructor
public class AdminGuideController extends BaseController {

    private final UserGuideService guideService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','get')")
    public ResponseEntity<?> list(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "") String search,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(
                guideService.list(status, search, safeSize, safePage * safeSize),
                guideService.count(status, search),
                safePage,
                safeSize)));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','get')")
    public ResponseEntity<?> forUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(guideService.find(userId).orElse(null)));
    }

    /** PUT rather than POST: granting twice leaves the same one row. */
    @PutMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','update')")
    public ResponseEntity<?> grant(@PathVariable UUID userId,
                                   @Valid @RequestBody(required = false) GrantGuideRequest request,
                                   @CurrentUser UUID operatorId) {
        GrantGuideRequest body = request == null ? new GrantGuideRequest() : request;
        return ResponseEntity.ok(ofSucceeded(guideService.grant(
                userId, body.getDisplayTitle(), body.getNote(), body.getLocationImageIds(), operatorId)));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','update')")
    public ResponseEntity<?> revoke(@PathVariable UUID userId,
                                    @RequestParam(required = false) String reason,
                                    @CurrentUser UUID operatorId) {
        return ResponseEntity.ok(ofSucceeded(guideService.revoke(userId, reason, operatorId)));
    }
}
