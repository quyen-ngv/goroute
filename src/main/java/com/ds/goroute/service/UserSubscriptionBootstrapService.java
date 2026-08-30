package com.ds.goroute.service;

import com.ds.goroute.mapper.AiTripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lazily creates default subscriptions in their own transaction so callers
 * inside a read-only transaction (e.g. check-in context) can still bootstrap.
 */
@Service
@RequiredArgsConstructor
public class UserSubscriptionBootstrapService {

    private final AiTripMapper aiTripMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(UUID userId) {
        aiTripMapper.ensureSubscription(userId);
    }
}
