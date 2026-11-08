package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.SettlementRequest;
import com.ds.goroute.dto.response.SettlementResponse;
import com.ds.goroute.service.expense.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Payments between two people of a trip (epic 05 §2.4f). Recording is open to the trip owner and
 * every accepted member; changing, deleting and restoring to whoever recorded it, either side or
 * the trip owner ({@link SettlementService}).
 */
@RestController
@Validated
@RequestMapping("/v1/api/settlements")
@RequiredArgsConstructor
public class SettlementController extends BaseController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<BaseResponse<SettlementResponse>> create(
            @Valid @RequestBody SettlementRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(settlementService.create(request, userId)));
    }

    @PutMapping("/{settlementId}")
    public ResponseEntity<BaseResponse<SettlementResponse>> update(
            @PathVariable UUID settlementId,
            @Valid @RequestBody SettlementRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(settlementService.update(settlementId, request, userId)));
    }

    /** Soft delete: the payment can be restored. */
    @DeleteMapping("/{settlementId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID settlementId,
            @CurrentUser UUID userId) {
        settlementService.delete(settlementId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{settlementId}/restore")
    public ResponseEntity<BaseResponse<SettlementResponse>> restore(
            @PathVariable UUID settlementId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(settlementService.restore(settlementId, userId)));
    }
}
