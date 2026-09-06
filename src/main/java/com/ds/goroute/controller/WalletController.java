package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.WalletReminderResponse;
import com.ds.goroute.dto.response.WalletResponse;
import com.ds.goroute.service.WalletService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/api/wallet")
@RequiredArgsConstructor
public class WalletController extends BaseController {
    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<BaseResponse<WalletResponse>> getWallet(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int debtLimit,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(walletService.getWallet(userId, page, size, debtLimit)));
    }

    @PostMapping("/expenses/{expenseId}/splits/{splitId}/reminders")
    public ResponseEntity<BaseResponse<WalletReminderResponse>> sendReminder(
            @PathVariable UUID expenseId,
            @PathVariable UUID splitId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(walletService.sendReminder(expenseId, splitId, idempotencyKey, userId)));
    }
}
