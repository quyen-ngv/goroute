package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.SocialSavedSpotResponse;
import com.ds.goroute.service.SocialSavedSpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/social-location/spots")
@RequiredArgsConstructor
public class SocialSavedSpotController extends BaseController {
    private final SocialSavedSpotService service;

    @GetMapping
    public ResponseEntity<BaseResponse<List<SocialSavedSpotResponse>>> list(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ofSucceeded(service.listMine(userId, page, size)));
    }

    @DeleteMapping("/{spotId}")
    public ResponseEntity<BaseResponse<Void>> delete(@CurrentUser UUID userId, @PathVariable UUID spotId) {
        service.delete(userId, spotId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
