package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.ConsentUpdateRequest;
import com.ds.goroute.dto.response.ConsentStateResponse;
import com.ds.goroute.service.consent.ConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The caller's consent to the Terms, the Privacy Policy and each optional purpose (V278). */
@RestController
@RequestMapping("/v1/api/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<ConsentStateResponse>> me(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(consentService.state(userId)));
    }

    /** Records answers; returns the new state. */
    @PostMapping
    public ResponseEntity<BaseResponse<ConsentStateResponse>> record(
            @CurrentUser UUID userId, @Valid @RequestBody ConsentUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(consentService.record(userId, request)));
    }
}
