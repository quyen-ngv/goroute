package com.ds.goroute.controller;

import com.ds.goroute.dto.request.ApplyReferralCodeRequest;
import com.ds.goroute.dto.response.ReferralStatusResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.ReferralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/referrals")
@RequiredArgsConstructor
public class ReferralController extends BaseService {
    private final ReferralService referralService;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestAttribute("userId") UUID userId) {
        ReferralStatusResponse status = referralService.getStatus(userId);
        return ResponseEntity.ok(ofSucceeded(status));
    }

    @PostMapping("/apply-code")
    public ResponseEntity<?> applyCode(@RequestAttribute("userId") UUID userId,
                                       @Valid @RequestBody ApplyReferralCodeRequest request) {
        referralService.applyCode(userId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
