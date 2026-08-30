package com.ds.goroute.service;

import com.ds.goroute.mapper.StarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lazily creates star wallets in their own transaction so callers inside a
 * read-only transaction (e.g. passport summary) can still bootstrap a wallet.
 */
@Service
@RequiredArgsConstructor
public class StarWalletBootstrapService {

    private final StarMapper starMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(UUID userId) {
        starMapper.createWallet(userId);
    }
}
