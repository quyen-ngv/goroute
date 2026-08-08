package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;
import com.ds.goroute.service.SocialLocationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/social-location")
@RequiredArgsConstructor
public class AdminSocialLocationController {
    private final SocialLocationAdminService service;

    @GetMapping("/restrictions")
    public ResponseEntity<BaseResponse<List<SocialLocationUserRestriction>>> restrictions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listRestrictions(status, page, size)));
    }

    @GetMapping("/restrictions/{userId}/events")
    public ResponseEntity<BaseResponse<List<SocialLocationSubmissionEvent>>> events(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listUserEvents(userId, limit)));
    }

    @DeleteMapping("/restrictions/{userId}")
    public ResponseEntity<BaseResponse<Void>> reset(@PathVariable UUID userId) {
        service.resetRestriction(userId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }
}
