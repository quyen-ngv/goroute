package com.ds.goroute.repository;

import com.ds.goroute.entity.UserConsent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of the consent history (V278 {@code user_consents}), append-only. */
public interface UserConsentRepository {

    void insert(UserConsent consent);

    /** The newest row of each purpose the person has answered. */
    List<UserConsent> findLatestByUser(UUID userId);

    /** The newest answer for one purpose; empty when never asked. */
    Optional<Boolean> findLatestGranted(UUID userId, String purpose);
}
