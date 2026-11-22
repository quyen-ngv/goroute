package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.AiCreditTransactionPageResponse;
import com.ds.goroute.dto.response.AiCreditWalletResponse;
import com.ds.goroute.service.AiCreditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The signed-in user's AI credits. Live changes arrive as {@code credit.changed} on the user topic. */
@Validated
@RestController
@RequestMapping("/v1/api/me/ai-credits")
@RequiredArgsConstructor
public class AiCreditController extends BaseController {
    private final AiCreditService service;

    @GetMapping
    public ResponseEntity<BaseResponse<AiCreditWalletResponse>> wallet(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.wallet(userId)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<AiCreditTransactionPageResponse>> transactions(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ofSucceeded(service.transactions(userId, page, size)));
    }
}
