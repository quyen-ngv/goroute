package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AdminAiCreditAdjustRequest;
import com.ds.goroute.dto.response.AiCreditTransactionPageResponse;
import com.ds.goroute.dto.response.AiCreditWalletResponse;
import com.ds.goroute.service.AiCreditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** One user's AI credits in the console: wallet, ledger, and an operator's correction. */
@Validated
@RestController
@RequestMapping("/v1/api/admin/users/{userId}/ai-credits")
@RequiredArgsConstructor
public class AdminAiCreditController extends BaseController {
    private final AiCreditService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'users','get')")
    public ResponseEntity<BaseResponse<AiCreditWalletResponse>> wallet(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.wallet(userId)));
    }

    @GetMapping("/transactions")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','get')")
    public ResponseEntity<BaseResponse<AiCreditTransactionPageResponse>> transactions(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ofSucceeded(service.transactions(userId, page, size)));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','update')")
    public ResponseEntity<BaseResponse<AiCreditWalletResponse>> adjust(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminAiCreditAdjustRequest request,
            @CurrentUser UUID operatorId) {
        return ResponseEntity.ok(ofSucceeded(service.adjust(userId, request.getAmount(), request.getReason(), operatorId)));
    }
}
