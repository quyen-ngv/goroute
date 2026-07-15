package com.ds.goroute.controller;

import com.ds.goroute.dto.response.StarWalletResponse;
import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.StarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/me/stars")
@RequiredArgsConstructor
public class StarController extends BaseService {
    private final StarService starService;

    @GetMapping
    public ResponseEntity<?> getWallet(@RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(starService.getWallet(userId)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@RequestAttribute("userId") UUID userId) {
        List<StarTransaction> transactions = starService.getTransactions(userId);
        return ResponseEntity.ok(ofSucceeded(transactions));
    }

    @PostMapping("/unlock-trip")
    public ResponseEntity<?> unlockTrip(@RequestAttribute("userId") UUID userId) {
        StarWalletResponse response = starService.unlockTrip(userId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
