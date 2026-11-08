package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.StarExchangeRequest;
import com.ds.goroute.dto.response.StarWalletResponse;
import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.StarWalletService;
import jakarta.validation.Valid;
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
    private final StarWalletService starWalletService;

    @GetMapping
    public ResponseEntity<?> getWallet(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(starWalletService.wallet(userId)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@CurrentUser UUID userId) {
        List<StarTransaction> transactions = starService.getTransactions(userId, 50, 0);
        return ResponseEntity.ok(ofSucceeded(transactions));
    }

    /** Exchanges Stars one way into credits (AI_CREDIT.STAR_EXCHANGE_*). */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@CurrentUser UUID userId, @Valid @RequestBody StarExchangeRequest request) {
        StarWalletResponse response = starWalletService.exchange(userId, request.getStars());
        return ResponseEntity.ok(ofSucceeded(response));
    }

    /** LEGACY-STAR-QUOTA: app builds up to 21; exchanges one trip's worth of Stars. */
    @Deprecated
    @PostMapping("/unlock-trip")
    public ResponseEntity<?> unlockTrip(@CurrentUser UUID userId) {
        StarWalletResponse response = starWalletService.unlockTrip(userId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
