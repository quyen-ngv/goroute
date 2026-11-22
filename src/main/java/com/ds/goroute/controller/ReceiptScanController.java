package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.ReceiptScanAccessResponse;
import com.ds.goroute.dto.response.ReceiptScanResponse;
import com.ds.goroute.service.ReceiptScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Receipt photo -> expense fields. Not tied to a trip: the same scan serves personal expenses. */
@RestController
@RequestMapping("/v1/api/expenses/receipt-scan")
@RequiredArgsConstructor
public class ReceiptScanController extends BaseController {
    private final ReceiptScanService receiptScanService;

    /** Whether the app shows the scan button, and today's allowance. */
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<ReceiptScanAccessResponse>> me(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(receiptScanService.access(userId)));
    }

    /** JPEG, PNG or WebP up to 6 MB. Counts against the daily allowance only when the model answered. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<ReceiptScanResponse>> scan(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "currencyHint", required = false) String currencyHint,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(receiptScanService.scan(userId, file, currencyHint)));
    }
}
