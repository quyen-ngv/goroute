package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.response.StarWalletResponse;
import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.service.StarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/me/stars")
@RequiredArgsConstructor
public class StarController extends BaseController {
    private final StarService starService;

    @GetMapping
    public ResponseEntity<?> getWallet(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(starService.getWallet(userId)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@CurrentUser UUID userId) {
        List<StarTransaction> transactions = starService.getTransactions(userId, 50, 0);
        return ResponseEntity.ok(ofSucceeded(transactions));
    }

    @PostMapping("/unlock-trip")
    public ResponseEntity<?> unlockTrip(@CurrentUser UUID userId) {
        StarWalletResponse response = starService.unlockTrip(userId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
