package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.dto.request.PersonalExpenseRequest;
import com.ds.goroute.dto.response.PersonalExpenseResponse;
import com.ds.goroute.service.impl.PersonalExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Expenses outside any trip: the caller's own, or split with friends. Listed through the Wallet. */
@RestController
@RequestMapping("/v1/api/personal-expenses")
@RequiredArgsConstructor
public class PersonalExpenseController extends BaseController {
    private final PersonalExpenseService personalExpenseService;

    @PostMapping
    public ResponseEntity<BaseResponse<PersonalExpenseResponse>> create(
            @Valid @RequestBody PersonalExpenseRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(personalExpenseService.create(request, userId)));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<BaseResponse<PersonalExpenseResponse>> get(
            @PathVariable UUID expenseId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(personalExpenseService.get(expenseId, userId)));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<BaseResponse<PersonalExpenseResponse>> update(
            @PathVariable UUID expenseId,
            @Valid @RequestBody PersonalExpenseRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(personalExpenseService.update(expenseId, request, userId)));
    }

    /** Settle or reopen one share: the payer for anyone, a participant for their own. */
    @PatchMapping("/{expenseId}/splits/{splitId}/mark-paid")
    public ResponseEntity<BaseResponse<PersonalExpenseResponse>> markSplit(
            @PathVariable UUID expenseId,
            @PathVariable UUID splitId,
            @RequestBody(required = false) MarkPaymentRequest request,
            @CurrentUser UUID userId) {
        boolean paid = request == null || request.getIsPaid() == null || request.getIsPaid();
        return ResponseEntity.ok(ofSucceeded(personalExpenseService.markSplit(expenseId, splitId, paid, userId)));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID expenseId,
            @CurrentUser UUID userId) {
        personalExpenseService.delete(expenseId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
