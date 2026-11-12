package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserConsent;
import com.ds.goroute.mapper.UserConsentMapper;
import com.ds.goroute.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserConsentRepositoryImpl implements UserConsentRepository {

    private final UserConsentMapper mapper;

    @Override
    public void insert(UserConsent consent) {
        mapper.insert(consent);
    }

    @Override
    public List<UserConsent> findLatestByUser(UUID userId) {
        return mapper.findLatestByUser(userId);
    }

    @Override
    public Optional<Boolean> findLatestGranted(UUID userId, String purpose) {
        return Optional.ofNullable(mapper.findLatestGranted(userId, purpose));
    }
}
